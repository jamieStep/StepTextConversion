package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.osisinputonly.Osis_DetermineReversificationTypeEtc
import org.stepbible.textconverter.osisinputonly.Osis_CrossReferenceChecker
import org.stepbible.textconverter.subelements.*
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Document


/****************************************************************************/
/**
 * This object expects to receive standard or expanded OSIS as input.  It
 * has one main task and one subsidiary one.  Its main task is to generate
 * a temporary OSIS file in standard OSIS syntax, but possibly modified so
 * that after feeding it through osis2mod, it will produce a module which
 * STEPBible renders properly.  And its subsidiary task (where the run is
 * starting either from VL or from USX) is to save a standard version of the
 * OSIS which could be used for future input (perhaps after manual tweaks
 * have been applied to it).
 *
 * Note that the two versions of OSIS are not identical.  The version saved
 * for future use retains teh original semantic and formatting markup as far
 * as possible.  The version fed to osis2mod may have been modified to
 * account for the fact that there are certain OSIS constructs which STEPBible
 * does not render well.
 *
 * @author ARA "Jamie" Jamieson
 */

object PE_Phase2_ToTempOsis : PE
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Converting Internal OSIS to version required for Sword module"
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor) {}
  override fun pre () { StepFileUtils.deleteFolder(FileLocations.getTempOsisFolderPath()) }
  override fun process () = doIt()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Be very careful if you intend to change the order of things below. */

  private fun doIt ()
  {
    /***************************************************************************/
    fun x () = Logger.announceAllAndTerminateImmediatelyIfErrors()



    /***************************************************************************/
    /* Sort out the input data. */

    StepFileUtils.createFolderStructure(FileLocations.getTempOsisFolderPath())
    selectInitialData()
    val doc = OsisTempDataCollection.getDocument()
    DataCollection = OsisTempDataCollection
    Dbg.d(OsisTempDataCollection.getDocument())



    /***************************************************************************/
    /* If at some future point you decide to work directly with USX, rather than
       OSIS, the stuff in this paragraph is unique to OSIS -- there may possibly
       be USX analogues, but they're likely to be different enough that I've
       decided there is little point in trying to set up common structures. */

    ProtocolConverterStandardOsisToExtendedOsis.process(doc) // It's safe to do this, regardless of whether the input is in standard or extended form.
    SE_BasicValidator(OsisTempDataCollection).structuralValidation(OsisTempDataCollection) // Checks for basic things like all verses being under chapters.



    /***************************************************************************/
    /* If at some future point you decide to work directly with USX, rather than
       OSIS, these are common classes, and you're likely to want to make use of
       some or all of these in some order or other. */

Dbg.outputDom(OsisTempDataCollection.getDocument())
    SE_CrossBoundaryMarkupHandler(OsisTempDataCollection).process(); x(); Dbg.outputDom(OsisTempDataCollection.getDocument())
    SE_TableHandler(OsisTempDataCollection).process(); x()
    SE_SubverseCollapser(OsisTempDataCollection).process(); x()
    SE_ElisionHandler(OsisTempDataCollection).process(); x()
    SE_StrongsHandler(OsisTempDataCollection).process(); x()
    Osis_CrossReferenceChecker.process(OsisTempDataCollection); x()
    SE_CalloutStandardiser(OsisTempDataCollection).process(); x()
    SE_CanonicalHeadingsHandler(OsisTempDataCollection).process(); x()
    SE_ListEncapsulator(OsisTempDataCollection).process(); x()



    /***************************************************************************/
    /* If you do need to revert to doing stuff in USX, you'll probably want
       analogues of the following, but you will probably need to write stuff
       specifically for USX. */

    handleReversification(); x()
    removeTemporarySupportStructure(OsisTempDataCollection)
    ContentValidator.process(OsisTempDataCollection, Osis_FileProtocol, OsisPhase2SavedDataCollection, Osis_FileProtocol)
    EmptyVerseHandler(OsisTempDataCollection).markVersesWhichWereEmptyInTheRawText()
    EmptyVerseHandler.preventSuppressionOfEmptyVerses(OsisTempDataCollection)
    SE_FeatureCollector(OsisTempDataCollection).process()
    ProtocolConverterExtendedOsisToStepOsis.process(doc)
    Dom.outputDomAsXml(doc, FileLocations.getTempOsisFilePath(), null)
  }


  /***************************************************************************/
  /* Time to worry about reversification.  First off, we need to read the
     data and determine how far adrift of NRSVA we are, so we can decide what
     kind of reversification (if any) might be appropriate. */

  private fun handleReversification ()
  {
    ReversificationData.process(OsisTempDataCollection.BibleStructure)
    Osis_DetermineReversificationTypeEtc.process(OsisTempDataCollection.BibleStructure)

    when (ConfigData["stepReversificationType"]!!.lowercase())
    {
      "runtime" ->
      {
        SE_RuntimeReversificationHandler(OsisTempDataCollection).process()
      }

      "conversiontime" ->
      {
        Osis_SE_ConversiontimeReversification(OsisTempDataCollection).process()
      }
    }
  }


  /****************************************************************************/
  /* Removes any temporary items added to support processing. */

  private fun removeTemporarySupportStructure (dataCollection: X_DataCollection)
  {
    Dbg.reportProgress("Removing temporary support details from OSIS.")
    dataCollection.getRootNodes().forEach {rootNode ->
      Dom.findNodesByName(rootNode, dataCollection.getFileProtocol().tagName_verse(), false).filter { NodeMarker.hasDummy(it) }.forEach { Dom.deleteNode(it) }
      Dom.setNodeName(rootNode, "div")
    }

    NodeMarker.deleteAllMarkers(dataCollection)
  }


  /****************************************************************************/
  /* On entry, OsisPhase1OutputCollection contains the data upon which we are
     to work.  We need a working copy of this so that we can create the
     module.  And we also retain it in case we want to save it to serve as an
     input on later runs. */

  private fun selectInitialData ()
  {
    //File("C:/Users/Jamie/Desktop/a.usx").bufferedWriter().use { it.write(OsisPhase1OutputDataCollection.getText())}
    OsisTempDataCollection.loadFromText(OsisPhase1OutputDataCollection.getText(), false)
    OsisPhase2SavedDataCollection.loadFromText(OsisPhase1OutputDataCollection.getText(), false)
    OsisPhase1OutputDataCollection.clearAll()
    RefBase.setBibleStructure(OsisTempDataCollection.BibleStructure)
  }
}