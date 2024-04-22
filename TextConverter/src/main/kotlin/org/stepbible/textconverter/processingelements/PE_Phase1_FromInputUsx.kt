package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.usxinputonly.Usx_OsisCreator
import org.stepbible.textconverter.usxinputonly.Usx_Preprocessor
import org.stepbible.textconverter.usxinputonly.Usx_Tidier
import org.stepbible.textconverter.utils.UsxDataCollection


/******************************************************************************/
/**
* Takes data from InputUsx, applies any necessary pre-processing, and then
* converts the result to expanded OSIS in [OsOsisPhase1OutputDataCollection].
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
* The output forms the text element of [OsisPhase1OutputDataCollection].  Note
* that it is *not* fed into the parsed data structures of that item, nor its
* associated BibleStructure.
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
  override fun pre () { }
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
    RefBase.setBibleStructure(UsxDataCollection.getBibleStructure())
    UsxDataCollection.loadFromFolder(FileLocations.getInputUsxFolderPath(), FileLocations.getFileExtensionForUsx())
    Usx_Preprocessor.processXslt(UsxDataCollection)
    Usx_Tidier.process(UsxDataCollection)
    if (!ConfigData.getAsBoolean("stepEvaluateSchemesOnly", "no"))
       Usx_OsisCreator.process(UsxDataCollection)
    RefBase.setBibleStructure(null)
  }
}