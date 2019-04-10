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
* Copyright 2003, 2004, 2005, 2006, 2007, 2008, 2009 Peter Baumann /
rasdaman GmbH.
*
* For more information please see <http://www.rasdaman.org>
* or contact Peter Baumann via <baumann@rasdaman.com>.
*/
// -*-C++-*- (for Emacs)

/*************************************************************
 *
 *
 * PURPOSE:
 *   The ULongType class is the superclass for all classes
 *   describing the type of a cell
 *
 *
 * COMMENTS:
 *   This file is patched by O2!
 *
 ************************************************************/

#ifndef _OCTETTYPE_HH_
#define _OCTETTYPE_HH_

#include <iosfwd>         // for ostream
#include "integraltype.hh"  // for IntegralType
#include "raslib/odmgtypes.hh"     // for r_Long

class OId;

//@ManMemo: Module: {\bf relcatalogif}.

/*@Doc:

OctetType is the base type used for boolean cell values (e.g.  result
of comparison operations, see \Ref{Ops}). The value of a Octet is
stored in one char. OctetType is a persistence capable class.
*/

/**
  * \ingroup Relcatalogifs
  */
class OctetType : public IntegralType
{
public:
    OctetType(const OId &id);

    OctetType();
    /*@Doc:
    default constructor, no initialization needed for OctetType.
    */

    OctetType(const OctetType &old);
    /*@Doc:
    copy constructor.
    */

    OctetType &operator=(const OctetType &old);
    /*@Doc:
    assignment operator.
    */

    ~OctetType() override;
    /*@Doc:
    virtual destructor.
    */

    void printCell(std::ostream &stream, const char *cell) const override;
    /*@Doc:
    */

    r_Long *convertToCLong(const char *cell, r_Long *value) const override;
    /*@Doc:
    */

    char *makeFromCLong(char *cell, const r_Long *value) const override;

    static const char *Name;

protected:
    void readFromDb() override;
    /*@Doc:
    initializes the attributes of this type.
    there is no database activity.  this is hard coded.
    */
};

#endif
