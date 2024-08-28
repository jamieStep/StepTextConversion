package org.stepbible.textconverter.builders

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.utils.*
import java.io.File



/******************************************************************************/
/**
  * Carries out the processing required to create the output implied by the
  * class name.
  *
  * @author ARA "Jamie" Jamieson
  */

object Builder_InitialOsisRepresentationFromOsis: Builder
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = ""
  override fun commandLineOptions () = null





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun doIt ()
  {
    Dbg.reportProgress(banner())
    Phase1TextOutput = File(FileLocations.getInputOsisFilePath()!!).readText().replace("\u000c", "") }
}
