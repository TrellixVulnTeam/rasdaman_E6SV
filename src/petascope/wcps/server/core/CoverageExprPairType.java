/*
 * This file is part of PetaScope.
 *
 * PetaScope is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * PetaScope is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with PetaScope. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information please see <http://www.PetaScope.org>
 * or contact Peter Baumann via <baumann@rasdaman.com>.
 *
 * Copyright 2009 Jacobs University Bremen, Peter Baumann.
 */
package petascope.wcps.server.core;

import petascope.wcps.server.exceptions.InvalidCrsException;
import petascope.wcps.server.exceptions.WCPSException;
import org.w3c.dom.*;

public class CoverageExprPairType implements IRasNode, ICoverageInfo {

    private IRasNode first, second;
    private CoverageInfo info;
    private boolean ok = false;

    public CoverageExprPairType(Node node, XmlQuery xq)
            throws WCPSException, InvalidCrsException {
        String nodeName = node.getNodeName();

        System.err.println("Trying to parse a coverage expression pair ... Starting at node "
                + nodeName);

        // Combination 1: CoverageExprType + ScalarExprType
        if (ok == false) {
            try {
                first = new CoverageExpr(node, xq);
                second = new ScalarExpr(node.getNextSibling(), xq);
                info = new CoverageInfo(((ICoverageInfo) first).getCoverageInfo());
                ok = true;
            } catch (WCPSException e) {
                System.err.println("Failed to parse CoverageExprType + ScalarExprType!");
            }
        }

        // Combination 2: ScalarExprType + CoverageExprType
        if (ok == false) {
            try {
                first = new ScalarExpr(node, xq);
                second = new CoverageExpr(node.getNextSibling(), xq);
                info = new CoverageInfo(((ICoverageInfo) second).getCoverageInfo());
                ok = true;
            } catch (WCPSException e) {
                System.err.println("Failed to parse ScalarExprType + CoverageExprType!");
            }
        }

        // Combination 3: CoverageExprType + CoverageExprType
        if (ok == false) {
            try {
                first = new CoverageExpr(node, xq);
                second = new CoverageExpr(node.getNextSibling(), xq);
                info = new CoverageInfo(((ICoverageInfo) first).getCoverageInfo());
                ok = true;
            } catch (WCPSException e) {
                System.err.println("Failed to parse a CoverageExprType + CoverageExprType!");
            }
        }

        

        if (ok == false) {
            throw new WCPSException("Could not parse a coverage expression pair !");
        }
    }

    public CoverageInfo getCoverageInfo() {
        return info;
    }

    public String toRasQL() {
        if (ok == true) {
            return first.toRasQL() + second.toRasQL();
        } else {
            return " error ";
        }
    }

    public IRasNode getFirst() {
        return first;
    }

    public IRasNode getSecond() {
        return second;
    }
}
