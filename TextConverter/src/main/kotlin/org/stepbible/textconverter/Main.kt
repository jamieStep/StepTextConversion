/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.applicationspecificutils.ThrowAwayCode
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg


/******************************************************************************/
/**
* Main program.
*
* @author ARA "Jamie" Jamieson
*/

/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                                  Public                                  **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/**
 * The converter main program.
 *
 * @param args Command line arguments.
 */

fun main (args: Array<String>)
{
  //Dbg.setBooksToBeProcessed("Zec")
  //ThrowAwayCode().testCollectObjects1()
  MainProcessor().process(args)
}