package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.usxinputonly.Usx_OsisCreator
import org.stepbible.textconverter.usxinputonly.Usx_Preprocessor
import org.stepbible.textconverter.usxinputonly.Usx_Tidier
import org.stepbible.textconverter.utils.OsisPhase1OutputDataCollection


/******************************************************************************/
/**
* Takes data from InputUsx, applies any necessary pre-processing, and then
* converts the result to expanded OSIS.
*
* In a previous implementation, I did a lot of work here.  In this latest
* incarnation all of that work has been deferred to later in the processing
* chain, and is applied not to the USX but to the OSIS generated from it.
* It is important that the work in this present class should therefore be kept
* to an absolute minimum.  It's ok to apply simple modifications to the OSIS,
* for example to correct systematic errors in it; but you should avoid changing
* the verse structure, expanding elisions etc.
*
* (I have in fact left in the code some provision for moving this more
* sophisticated processing back into USX should we ever need to do so -- see the
* discussion in the user and maintenance guide.  However there are good reasons
* at present for retaining it in the OSIS side of the system, and so long as
* this remains the case, you should avoid doing anything very much here.)
*
* Output goes to [OsisPhase1OutputDataCollection].
*
* @author ARA "Jamie" Jamieson
*/

object PE_Phase1_FromInputUsx: PE
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Preparing USX and converting to OSIS"
  override fun getCommandLineOptions(commandLineProcessor: CommandLineProcessor) {}
  override fun pre () = StepFileUtils.deleteFolder(FileLocations.getInternalOsisFolderPath())
  override fun process () = doIt()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doIt ()
  {
    val items = Usx_Preprocessor.process() // Gets DOMs for each selected input file, after post-processing them.
    Usx_Tidier.process(items)
    Usx_OsisCreator.process(items)
  }
}