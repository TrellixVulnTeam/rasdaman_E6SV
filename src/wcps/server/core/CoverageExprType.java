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


package wcps.server.core;

import org.w3c.dom.*;

import java.util.Iterator;

public class CoverageExprType implements IRasNode, ICoverageInfo
{
	private IRasNode child;
	private String childInfo;
	private CoverageInfo info;
	private boolean simpleCoverage;    // True is the coverage is just a string

	public CoverageExprType(Node node, ProcessCoveragesRequest pcr) throws WCPSException
	{
		while ((node != null) && node.getNodeName().equals("#text"))
		{
			node = node.getNextSibling();
		}

		if (node == null)
		{
			throw new WCPSException("CoverageExprType parsing error!");
		}

		String nodeName = node.getNodeName();

		simpleCoverage = false;
		System.err.println("CoverageExprType: node " + nodeName);

		if (nodeName.equals("coverage"))
		{
			simpleCoverage = true;
			childInfo      = node.getFirstChild().getNodeValue();

			if (!pcr.isIteratorDefined(childInfo))
			{
				throw new WCPSException("Iterator " + childInfo + " not defined!");
			}

			Iterator<String> coverages = pcr.getCoverages(childInfo);

			info = new CoverageInfo(pcr.getMetadataSource().read(coverages.next()));

			while (coverages.hasNext())
			{    // Check if all the coverages are compatible
				CoverageInfo tmp = new CoverageInfo(
						       pcr.getMetadataSource().read(
							       coverages.next()));

				if (!tmp.isCompatible(info))
				{
					throw new WCPSException(
					    "Incompatible coverages within the same iterator");
				}
			}

			System.err.println("Found simple coverage definition: " + childInfo + ", "
					   + info.toString());
		}
		else if (nodeName.equals("crsTransform"))
		{
            // TODO: implement CrsTransform class
			child = new CrsTransformCoverageExprType(node, pcr);
		}
        else if (nodeName.equals("scale"))
		{
            // TODO: implement class ScaleCoverageExprType
			child = new ScaleCoverageExprType(node, pcr);
		}
		else if (nodeName.equals("construct"))
		{
            // TODO: implement ConstructCoverageExprType class
			child = new ConstructCoverageExprType(node, pcr);
		}
        else if (nodeName.equals("const"))
		{
            // TODO: implement class ConstantCoverageExprType
			child = new ConstantCoverageExprType(node.getFirstChild(), pcr);
		}
		else
		{    // Try one of the groups
            child = null;

            if (child == null)
			{
				try
				{
					child = new SetMetadataCoverageExprType(node, pcr);
					System.err.println("Matched set metadata operation.");
				}
				catch (WCPSException e)
				{
					System.err.println("Failed to match set metadata operation: "
							   + e.toString() + "\nRetrying");
					child = null;
				}
			}

            if (child == null)
            {
                try
                {
                    child = new InducedOperationCoverageExprType(node, pcr);
                    System.err.println("Matched induced coverage expression operation.");
                }
                catch (WCPSException e)
                {
                    System.err.println("Failed to match induced coverage expression operation: "
                               + e.toString() + "\nRetrying");
                    child = null;
                    if (e.getMessage().equals("Method not implemented"))
                        throw e;
                }
            }

            if (child == null)
			{
				try
				{
					child = new SubsetOperationCoverageExprType(node, pcr);
					System.err.println("Matched subset operation.");
				}
				catch (WCPSException e)
				{
					System.err.println("Failed to match subset operation: "
							   + "\nRetrying");
					child = null;
				}
			}

		}

		if (!simpleCoverage && (child == null))
		{
			throw new WCPSException("Invalid coverage Expression, next node: "
						+ node.getNodeName());
		}

		if (info == null)
		{
			System.err.println(node.getNodeName());
			info = new CoverageInfo(((ICoverageInfo) child).getCoverageInfo());
		}
	}

	public CoverageInfo getCoverageInfo()
	{
		return info;
	}

	public String toRasQL()
	{
		if (simpleCoverage)
		{
			return childInfo;
		}
		else
		{
			return child.toRasQL();
		}
	}
}


;
