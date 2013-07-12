/*
 * This file is part of rasdaman community.
 *
 * Rasdaman community is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rasdaman community is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with rasdaman community.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2003 - 2010 Peter Baumann / rasdaman GmbH.
 *
 * For more information please see <http://www.rasdaman.org>
 * or contact Peter Baumann via <baumann@rasdaman.com>.
 */
package petascope.wcs2.extensions;

import java.math.BigInteger;
import java.util.List;
import java.util.ListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import petascope.core.CoverageMetadata;
import petascope.core.DbMetadataSource;
import petascope.exceptions.ExceptionCode;
import petascope.exceptions.PetascopeException;
import petascope.exceptions.SecoreException;
import petascope.exceptions.WCSException;
import petascope.util.Pair;
import petascope.util.WcsUtil;
import petascope.util.XMLSymbols;
import petascope.util.ras.RasQueryResult;
import petascope.wcps.server.core.CellDomainElement;
import petascope.wcs2.handlers.Response;
import petascope.wcs2.parsers.GetCoverageMetadata;
import petascope.wcs2.parsers.GetCoverageRequest;
import petascope.wcs2.parsers.GetCoverageRequest.DimensionSlice;
import petascope.wcs2.parsers.GetCoverageRequest.DimensionSubset;
import petascope.wcs2.parsers.GetCoverageRequest.DimensionTrim;
import petascope.wcs2.templates.Templates;

/**
 * Return coverage as pure GML.
 *
 * @author <a href="mailto:d.misev@jacobs-university.de">Dimitar Misev</a>
 */
public class GmlFormatExtension extends AbstractFormatExtension {

    private static final Logger log = LoggerFactory.getLogger(GmlFormatExtension.class);
    public static final String DATATYPE_URN_PREFIX = "urn:ogc:def:dataType:OGC:1.1:"; // FIXME: now URNs are deprecated
    protected static final String MULTIPOINTSCHEMA = "ps_multipoint";
    protected static final String TAG_DATABLOCK = "DataBlock";
    protected static final String TAG_RANGEPARAMETERS = "rangeParameters";
    protected static final String TAG_TUPLELIST = "tupleList"; 

    @Override
    public boolean canHandle(GetCoverageRequest req) {
        return req.getFormat() == null || (!req.isMultipart() && getMimeType().equals(req.getFormat()));
        //return false;
    }

    @Override
    public Response handle(GetCoverageRequest request, DbMetadataSource meta)
            throws PetascopeException, WCSException, SecoreException {
        GetCoverageMetadata m = new GetCoverageMetadata(request, meta);
        
        // First, transform possible non-native CRS subsets
        CRSExtension crsExtension = (CRSExtension) ExtensionsRegistry.getExtension(ExtensionsRegistry.CRS_IDENTIFIER);
        crsExtension.handle(request, m);
        
        //Handle the range subset feature
        RangeSubsettingExtension rsubExt = (RangeSubsettingExtension) ExtensionsRegistry.getExtension(ExtensionsRegistry.RANGE_SUBSETTING_IDENTIFIER);
        rsubExt.handle(request, m);
        
        if (m.getCoverageType().equals(XMLSymbols.LABEL_MULTIPOINT_COVERAGE)) {
            Response r = handleMultiPoint(request, request.getCoverageId(), meta, m);
            String xml = r.getXml();
            return new Response(r.getData(), xml, r.getMimeType());
            
        } else if (m.getCoverageType().matches(".*" + XMLSymbols.LABEL_GRID_COVERAGE)) {
            
            // Use the GridCoverage template, which works with any subtype of AbstractGridCoverage via the {domainSetaddition}            
            try {
                // GetCoverage metadata was initialized with native coverage metadata, but subsets may have changed it:
                updateGetCoverageMetadata(request, m);
            } catch (PetascopeException pEx) {
                throw pEx;
            }
            
            String gml = WcsUtil.getGML(m, Templates.GRID_COVERAGE, true);
            gml = addCoverageData(gml, request, meta, m);
            
            // RGBV coverages
            if (m.getCoverageType().equals(XMLSymbols.LABEL_REFERENCEABLE_GRID_COVERAGE)) {
                gml = WcsUtil.addCoefficients(gml, m);
                // Grid and Coverage bounds need to be updated: there was coefficient knowledge before 
                updateGetCoverageMetadata(request, m);
                gml = WcsUtil.getBounds(gml, m);
                
            }
            return new Response(gml);
            
        } else {            
            throw new WCSException(ExceptionCode.UnsupportedCoverageConfiguration,
                    "The coverage type '" + m.getCoverageType() + "' is not supported.");                    
        }                
    }
    
    protected String addCoverageData(String gml, GetCoverageRequest request, DbMetadataSource meta, GetCoverageMetadata m) 
            throws WCSException, PetascopeException {
        RasQueryResult res = new RasQueryResult(executeRasqlQuery(request, m, meta, CSV_ENCODING, null).fst);
        if (!res.getMdds().isEmpty()) {
            String data = new String(res.getMdds().get(0));
            data = WcsUtil.csv2tupleList(data);
            data = "<" + TAG_DATABLOCK + ">" + 
                        "<" +  TAG_RANGEPARAMETERS + "/>" + 
                        "<" + TAG_TUPLELIST + ">" + data + "</" + TAG_TUPLELIST + ">" + 
                    "</" + TAG_DATABLOCK + ">";
            gml = gml.replace("{" + Templates.KEY_COVERAGEDATA + "}", data);
        }
        return gml;
    }
    
    /**
     * Handles a request for MultiPoint Coverages and returns a response XML
     * @param req
     * @param coverageID
     * @return
     * @throws WCSException
     */
    private Response handleMultiPoint(GetCoverageRequest req, String coverageID, DbMetadataSource meta, GetCoverageMetadata m)
            throws WCSException {
        CoverageMetadata cov = m.getMetadata();
        String ret = WcsUtil.getGML(m, Templates.MULTIPOINT_COVERAGE, false);
        String pointMembers = "";
        String low = "", high = "";

        try {

            List<CellDomainElement> cellDomainList = cov.getCellDomainList();

            /* check for subsetting */
            List<DimensionSubset> subsets = req.getSubsets();
            if (!subsets.isEmpty()) {

                /* subsetting ON: get coverage metadata */
                ListIterator<DimensionSubset> listIterator = subsets.
                        listIterator();
                while (listIterator.hasNext()) {

                    DimensionSubset subsetElement = listIterator.next();
                    String dimension = subsetElement.getDimension();
                    int dimIndex = cov.getDomainIndexByName(dimension);
                    CellDomainElement cellDomain = cellDomainList.get(dimIndex);

                    if (subsetElement instanceof DimensionTrim) {
                        DimensionTrim trim = (DimensionTrim) subsetElement;
                        cellDomain.setHi(new BigInteger(trim.getTrimHigh()));
                        cellDomain.setLo(new BigInteger(trim.getTrimLow()));
                        cellDomain.setSubsetElement(subsetElement);
                    }

                    if (subsetElement instanceof DimensionSlice) {
                        DimensionSlice slice = (DimensionSlice) subsetElement;
                        cellDomain.setHi(new BigInteger(slice.getSlicePoint()));
                        cellDomain.setLo(new BigInteger(slice.getSlicePoint()));
                        cellDomain.setSubsetElement(subsetElement);
                    }
                }
            }

            /* iterate the coverage metadata */
            for (CellDomainElement aCellDomainList : cellDomainList) {
                low += aCellDomainList.getLo() + " ";
                high += aCellDomainList.getHi() + " ";
            }

            /* get coverage data */
            pointMembers = meta.coverageData(MULTIPOINTSCHEMA, coverageID, req.
                    getCoverageId(), cellDomainList);
            Pair<String, String> pair = constructWcpsQuery(req, cov, CSV_ENCODING, null);

            /* generate the result */
            ret = ret.replaceAll("\\{" + Templates.KEY_POINTMEMBERS + "\\}", 
                    pointMembers).replaceAll("\\{" + Templates.KEY_LOW + "\\}", 
                    low).replaceAll("\\{"          + Templates.KEY_HIGH + "\\}", 
                    high).replaceAll("\\{"         + Templates.KEY_AXISLABELS + "\\}", 
                    pair.snd).replaceAll("\\{"     + Templates.KEY_MULUOMLABLES + "\\}", pair.snd);

        } catch (PetascopeException ex) {
            log.error("Error", ex);
        }
        return new Response(ret);
    }

    @Override
    public String getExtensionIdentifier() {
        return ExtensionsRegistry.ENCODING_IDENTIFIER;
    }

    public String getMimeType() {
        return MIME_GML;
    }
}
