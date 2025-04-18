/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.applicationspecificutils.ThrowAwayCode
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNames
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
  //ThrowAwayCode().testCollectObjects1()
  BibleBookNames.init()
  //Dbg.setBooksToBeProcessed("1Co", ">=")
  MainProcessor().process(args)
}