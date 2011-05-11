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

import java.math.BigInteger;
import petascope.exceptions.ExceptionCode;
import petascope.exceptions.WCPSException;
import petascope.wcs2.parsers.GetCoverageRequest;

//A coverage axis in pixel coordinates. See the WCPS standard.
public class CellDomainElement implements Cloneable {

    GetCoverageRequest.DimensionSubset subsetElement;

    private BigInteger hi;                      //FIXME: should be double
    private BigInteger lo;                      //FIXME: should be double

    public CellDomainElement(BigInteger lo, BigInteger hi) throws WCPSException {
        if ((lo == null) || (hi == null)) {
            throw new WCPSException(ExceptionCode.InvalidMetadata, 
                    "Invalid cell domain element: Bounds may not be null");
        }

        if (lo.compareTo(hi) == 1) {
            throw new WCPSException(ExceptionCode.InvalidMetadata, 
                    "Invalid cell domain element: Lower bound cannot be larger than upper bound");
        }

        this.lo = lo;
        this.hi = hi;

    }

    @Override
    public CellDomainElement clone() {
        try {
            return new CellDomainElement(BigInteger.ZERO.add(lo),
                    BigInteger.ZERO.add(hi));
        } catch (WCPSException ime) {
            throw new RuntimeException(
                    "Invalid metadata while cloning CellDomainElement. This is a software bug in WCPS.",
                    ime);
        }

    }

    public boolean equals(CellDomainElement cde) {
        return lo.equals(cde.lo) && hi.equals(cde.hi);

    }

    public BigInteger getHi() {
        return hi;

    }

    public void setHi(BigInteger hi){this.hi = hi;}

    public BigInteger getLo() {
        return lo;
    }

    public void setLo(BigInteger lo){this.lo = lo;}

    @Override
    public String toString() {
        String result = "CellDomainElement [" + lo + ", " + hi + "]";
        return result;
    }

    public GetCoverageRequest.DimensionSubset getSubsetElement() {
        return subsetElement;
    }

    public void setSubsetElement(GetCoverageRequest.DimensionSubset subsetElement) {
        this.subsetElement = subsetElement;
    }
}
