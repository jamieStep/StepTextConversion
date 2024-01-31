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

    IssueAndInformationRecorder = Osis_IssueAndInformationRecorder
    StepFileUtils.createFolderStructure(FileLocations.getTempOsisFolderPath())
    selectInitialData()
    val doc = OsisTempDataCollection.getDocument()
    DataCollection = OsisTempDataCollection



    /***************************************************************************/
    /* If at some future point you decide to work directly with USX, rather than
       OSIS, the stuff in this paragraph is unique to OSIS -- there may possibly
       be USX analogues, but they're likely to be different enough that I've
       decided there is little point in trying to set up common structures. */

    ProtocolConverterStandardOsisToExtendedOsis.process(doc) // It's safe to do this, regardless of whether the input is in standard or extended form.
    addTemporarySupportStructure(doc)  // Things which make processing easier, but which have to be undone later.
    Osis_BasicValidator.structuralValidation(OsisTempDataCollection) // Checks for basic things like all verses being under chapters.



    /***************************************************************************/
    /* If at some future point you decide to work directly with USX, rather than
       OSIS, these are common classes, and you're likely to want to make use of
       some or all of these in some order or other. */

    Osis_SE_CrossBoundaryMarkup.process(OsisTempDataCollection); x()
    Osis_SE_Tables.process(OsisTempDataCollection); x()
    Osis_SE_SubverseCollapser.process(OsisTempDataCollection); x()
    Osis_SE_Elisions.process(OsisTempDataCollection); x()
    Osis_SE_Strongs.process(OsisTempDataCollection); x()
    Osis_CrossReferenceChecker.process(OsisTempDataCollection); x()
    Osis_SE_CalloutStandardiser.process(OsisTempDataCollection); x()
    Osis_SE_CanonicalHeadings.process(OsisTempDataCollection); x()
    Osis_SE_ListEncapsulator.process(OsisTempDataCollection); x()



    /***************************************************************************/
    /* If you do need to revert to doing stuff in USX, you'll probably want
       analogues of the following, but you will probably need to write stuff
       specifically for USX. */

    handleReversification(); x()
    Osis_EmptyVerseHandler.annotateEmptyVerses(doc)
    removeTemporarySupportStructure(doc)
    Osis_BasicValidator.process(OsisTempDataCollection)
    ContentValidator.process(OsisTempDataCollection, Osis_FileProtocol, OsisPhase2SavedDataCollection, Osis_FileProtocol)
    Osis_SE_FeatureCollector.process(OsisTempDataCollection)
    ProtocolConverterExtendedOsisToStepOsis.process(doc)
    Dom.outputDomAsXml(doc, FileLocations.getTempOsisFilePath(), null)
  }


  /****************************************************************************/
  /* Adds a dummy verse at the end of each chapter so that any insertions can
     always go _before_ something. */
     
  private fun addDummyVerses (doc: Document)
  {
    Dom.findNodesByName(doc, "chapter").forEach { chapterNode ->
      val id = chapterNode["osisID"]!! + ".${RefBase.C_BackstopVerseNumber}"
      val dummySid = Dom.createNode(doc, "<verse sID='$id' osisID='$id'/>"); Utils.addTemporaryAttribute(dummySid, "_temp_dummy", "y")
      val dummyEid = Dom.createNode(doc, "<verse eID='$id' osisID='$id'/>"); Utils.addTemporaryAttribute(dummyEid, "_temp_dummy", "y")
      chapterNode.appendChild(dummySid)
      chapterNode.appendChild(dummyEid)
    }
  }


  /***************************************************************************/
  /* Adds temporary nodes etc to aid later processing. */

  private fun addTemporarySupportStructure (doc: Document)
  {
    Dbg.reportProgress("Adding temporary support details to OSIS.")

    addDummyVerses(doc)

    Dom.findNodesByAttributeValue(doc, "div", "type", "book").forEach {
      if (null != it["osisID"])
        Utils.addTemporaryAttribute(it, "_temp_id", it["osisID"]!!)
    }

    Dom.findNodesByName(doc, "chapter").forEach {
      if (null != it["osisID"])
        Utils.addTemporaryAttribute(it, "_temp_id", it["osisID"]!!)
    }

    Dom.findNodesByName(doc, "verse").forEach {
      if (null != it["sID"])
        Utils.addTemporaryAttribute(it, "_temp_id", it["sID"]!!)
      else if (null != it["eID"])
        Utils.addTemporaryAttribute(it, "_temp_id", it["eID"]!!)
    }
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
        Osis_SE_RuntimeReversification.process(OsisTempDataCollection)
      }

      "conversiontime" ->
      {
        Osis_SE_ConversiontimeReversification.process(OsisTempDataCollection)
      }
    }
  }


  /****************************************************************************/
  /* Removes any temporary items added to support processing. */

  private fun removeTemporarySupportStructure (doc: Document)
  {
    Dbg.reportProgress("Removing temporary support details from OSIS.")
    Dom.findNodesByAttributeName(doc, "verse", "_temp_dummy").forEach { Dom.deleteNode(it) }
    Dom.findNodesByName(doc, "book").forEach { Dom.setNodeName(it, "div") }
    Utils.deleteTemporaryAttributes(doc)
  }


  /****************************************************************************/
  /* On entry, OsisPhase1OutputCollection contains the data upon which we are
     to work.  This means that OsisPhase2InputCollection can be set equal to
     that, because that's what we now work with.

     However, we may also need to retain the original data.  Unless the input
     was OSIS (in which case we want to leave that OSIS as-is), we need a copy
     of this original data so we can save it later to the InputOsis folder for
     potential future manual tweaks and use as input.
   */

  private fun selectInitialData ()
  {
    /**************************************************************************/
    OsisTempDataCollection = OsisPhase1OutputDataCollection



    /**************************************************************************/
    /* Save the input data for use in validation and also in case we need to
       store it for possible use as an input in future runs (perhaps after
       tweaking it manually). */

    OsisPhase2SavedDataCollection.addFromText(OsisPhase1OutputDataCollection.getText(), false)
    OsisPhase1OutputDataCollection.clearText()
  }


}