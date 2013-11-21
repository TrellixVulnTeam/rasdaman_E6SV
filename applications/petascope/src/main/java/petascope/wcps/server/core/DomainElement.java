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
package petascope.wcps.server.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import petascope.exceptions.ExceptionCode;
import petascope.exceptions.WCPSException;
import petascope.util.AxisTypes;
import petascope.util.CrsUtil;
import petascope.util.WCPSConstants;

/**
 * This is an axis in geographic coordinates. See the WCPS standard.
 */
public class DomainElement implements Cloneable {
    
    private static Logger log = LoggerFactory.getLogger(DomainElement.class);

    private Set<String> crss;
    private String name;
    private Double numHi;
    private Double numLo;
    private String strHi;
    private String strLo;
    private String type;
    private String uom;
    private Collection<String> allowedAxes;
    private Double resolution = 1.0;

    public DomainElement(String name, String type, Double numLo, Double numHi, String strLo,
            String strHi, Set<String> crss, Collection<String> axes, String uom) throws WCPSException
            {
        this.allowedAxes = axes;
        this.uom = uom;

        if ((name == null) || (type == null)) {
            throw new WCPSException(ExceptionCode.InvalidMetadata, 
                    WCPSConstants.ERRTXT_INVALID_DOMAIN_ELEMENT_NULL);
        }

        if (name.equals("")) {
            throw new WCPSException(ExceptionCode.InvalidMetadata, 
                    WCPSConstants.ERRTXT_INVALID_DOMAIN_ELEMENT_EMPTY);
        }

        if (allowedAxes.contains(type) == false) {
            throw new WCPSException(ExceptionCode.InvalidMetadata, 
                    WCPSConstants.ERRTXT_INVALID_DOMAIN_ELEMENT_TYP_P1 + type
                    + WCPSConstants.ERRTXT_INVALID_DOMAIN_ELEMENT_TYP_P2 + allowedAxes.toString());
        }

        if ((numLo != null) && (numHi != null) && (strLo == null) && (strHi == null)) {
            if (numLo.compareTo(numHi) == 1) {
                throw new WCPSException(ExceptionCode.InvalidMetadata, 
                        WCPSConstants.ERRTXT_INVALID_DOM_LOWER_INT_BOUND);
            }

            this.numLo = numLo;
            this.numHi = numHi;
            
        } else if ((strLo != null) && (numHi != null) && (numLo == null) && (numHi == null)) {
            if (strLo.equals("") || strHi.equals("")) {
                throw new WCPSException(ExceptionCode.InvalidMetadata, 
                        WCPSConstants.ERRTXT_INVALID_DOM_STRING_BOUND);
            }

            this.strLo = strLo;
            this.strHi = strHi;
        } else {
            /* Allow both sources of info for time-axes */
            if (type.equals("t")) {
                this.strLo = strLo;
                this.strHi = strHi;
                this.numLo = numLo;
                this.numHi = numHi;
            } else {
                throw new WCPSException(ExceptionCode.InvalidMetadata, 
                        WCPSConstants.ERRTXT_INVALID_DOM_INT_BOUND_BOTH
                        + name + ":" + type);
            }
        }

        if ((type.equals(AxisTypes.X_AXIS) || type.equals(AxisTypes.Y_AXIS)) && (numLo == null)) {
            throw new WCPSException(ExceptionCode.InvalidMetadata, 
                    WCPSConstants.ERRTXT_INVALID_DOM_SPATIAL_AXIS);
        } else if (type.equals(AxisTypes.T_AXIS) && (numLo == null) || (numHi == null)) {
            throw new WCPSException(ExceptionCode.InvalidMetadata, WCPSConstants.ERRTXT_INVALID_DOM_T_AXIS);
        }

        this.name = name;
        this.type = type;

        if ((crss == null) || !crss.contains(CrsUtil.GRID_CRS)) {
//			throw new WCPSException(ExceptionCode.InvalidMetadata, 
//			    "Invalid domain element: CRS set does not contain image CRS '"
//			    + CrsUtil.GRID_CRS + "'");
            crss.add(CrsUtil.GRID_CRS);
        }

        this.crss = crss;
        
        log.trace(toString());
    }

    @Override
    public DomainElement clone() {
        Set<String> c = new HashSet<String>(crss.size());
        Iterator<String> i = crss.iterator();

        while (i.hasNext()) {
            c.add(new String(i.next()));
        }

        try {
            String newName = name == null ? null : new String(name);
            String newType = type == null ? null : new String(type);
            Double newNumLo = numLo == null ? null : new Double(numLo);
            Double newNumHi = numHi == null ? null : new Double(numHi);
            String newStrLo = strLo == null ? null : new String(strLo);
            String newStrHi = strHi == null ? null : new String(strHi);
            String newUom = uom == null ? null : new String(uom);
            return new DomainElement(newName, newType, newNumLo, newNumHi, newStrLo, newStrHi, c, allowedAxes, newUom);
        } catch (WCPSException ime) {
            throw new RuntimeException(
                    WCPSConstants.ERRTXT_INVALID_METADAT_WHILE_CLONE,
                    ime);
        }

    }

    public boolean equals(DomainElement de) {
        if ((numLo == null) && (de.numLo == null)) {
            return strLo.equals(de.strLo) && strHi.equals(strHi)
                    && name.equals(de.name) && type.equals(de.type);
        } else if ((strLo == null) && (de.strLo == null)) {
            return numLo.equals(de.numLo) && numHi.equals(numHi)
                    && name.equals(de.name) && type.equals(de.type);
        } else {
            return false;
        }
    }

    public String getName() {
        return name;
    }

    public Double getNumHi() {
        return numHi;
    }

    public Double getNumLo() {
        return numLo;
    }

    public String getStrHi() {
        return strHi;
    }

    public String getStrLo() {
        return strLo;
    }

    public String getType() {
        return type;
    }
    
    public Set<String> getCrsSet() {
        return crss;
    }

    /**
     * Extracts the external CRS from the allowed CRS of this domain (always containing the grid CRS as well, see constructor)
     * @return The external CRS of this dimension
     */
    public String getExternalCrs() {
        for (String crs : crss) {
            if (!crs.equals(CrsUtil.GRID_CRS)) {
                return crs;
            }
        }
        return "";
    }

    public String getUom() {
        return uom;
    }
    
    public Double getResolution() {
        return resolution;
    }
    
    public void setResolution(Integer DIM) {        
        // Use BigDecimals to avoid finite arithemtic rounding issues of Doubles
        BigDecimal maxBD = BigDecimal.valueOf(numHi);
        BigDecimal minBD = BigDecimal.valueOf(numLo);
        BigDecimal dimBD = BigDecimal.valueOf(DIM);
        BigDecimal diffBD = maxBD.subtract(minBD);
        BigDecimal resBD = diffBD.divide(dimBD, RoundingMode.UP);
        
        resolution = resBD.doubleValue();
    }

    @Override
    public String toString() {
        String d = WCPSConstants.MSG_DOMAIN_ELEMENT_NAME + name + "', " + WCPSConstants.MSG_TYPE_CAMEL + ": '" + type
                + "', " + WCPSConstants.MSG_NUMLOW + ": '" + numLo + "', " + WCPSConstants.MSG_NUMHI + ": '" + numHi + "', " + WCPSConstants.MSG_STRLOW + ": '"
                + strLo + "', " + WCPSConstants.MSG_STRHI + ": '" + strHi + "', " + WCPSConstants.MSG_CRS_SET_CAMEL + ": '" + crss + "'}";
        return d;
    }
}
