package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.StandardFileLocations


/******************************************************************************/
/**
 * High level debug controller.
 *
 * Lets debug appear to be like any other processor.  In particular, this gives
 * us the opportunity to add command-line flags, and to delete any existing
 * debug output file.
 *
 * @author ARA "Jamie" Jamieson
*/

object DbgController: TextConverterProcessorBase()
{
  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*           Required to cater for TextConverterProcessorBase               */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner (): String { return "" }
  override fun pre (): Boolean { deleteFile(Pair(StandardFileLocations.getDebugOutputFilePath(), null)); return true }
  override fun process (): Boolean { return true }
  override fun runMe (): Boolean { return true }


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    fun addDisplayOption (name: String, description: String)
    {
      val commonText = ": 'No' or anything containing 'screen' (output to screen), 'file' (output to debugLog.txt), or both.  Include 'deferred' if you want screen output at the end of the run, rather than as it occurs.  Not case-sensitive."
      commandLineProcessor.addCommandLineOption(name, 1, description + commonText, null, "no", false)
    }

    commandLineProcessor.addCommandLineOption("dbgAddDebugAttributesToNodes", 0, "Add debug attributes to nodes.", null, "no", false)
    addDisplayOption("dbgDisplayReversificationRows", "Display selected reversification rows")
  }
}
