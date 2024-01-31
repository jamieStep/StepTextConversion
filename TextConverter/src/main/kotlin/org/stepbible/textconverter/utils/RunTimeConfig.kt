package org.stepbible.textconverter.utils


/****************************************************************************/
/**
 * Run-time configuration flags and associated items.  This is rather like
 * CompileTimeConfig, in that it is made up basically of flags; the only
 * difference is that the values here can't be determined until run-time.
 *
 * Not to be confused with ConfigData, which predominantly holds data read
 * from config files.
 *
 * @author ARA "Jamie" Jamieson
 */



/****************************************************************************/
/* The default settings here are the ones appropriate to a run involving the
   Crosswire version of osis2mod. */

var C_CollapseSubverses = true
var C_CreateEmptyChapters = true
var C_ExpandElisions = true
