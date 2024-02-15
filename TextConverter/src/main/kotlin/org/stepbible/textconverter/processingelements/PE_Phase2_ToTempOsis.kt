package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.osisinputonly.Osis_DetermineReversificationTypeEtc
import org.stepbible.textconverter.osisinputonly.Osis_CrossReferenceChecker
import org.stepbible.textconverter.subelements.*
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.utils.*


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
    setupInitialData(); x()
    val doc = OsisTempDataCollection.getDocument()
    DataCollection = OsisTempDataCollection



    /***************************************************************************/
    /* If at some future point you decide to work directly with USX, rather than
       OSIS, the stuff in this paragraph is unique to OSIS -- there may possibly
       be USX analogues, but they're likely to be different enough that I've
       decided there is little point in trying to set up common structures. */

    SE_BasicValidator(OsisTempDataCollection).structuralValidation(OsisTempDataCollection) // Checks for basic things like all verses being under chapters.



    /***************************************************************************/
    SE_TableHandler(OsisTempDataCollection).process(); x()
    SE_SubverseCollapser(OsisTempDataCollection).process(); x()
    SE_ElisionHandler(OsisTempDataCollection).process(); x()
    SE_StrongsHandler(OsisTempDataCollection).process(); x()
    Osis_CrossReferenceChecker.process(OsisTempDataCollection); x()
    SE_CalloutStandardiser(OsisTempDataCollection).process(); x()
    SE_ListEncapsulator(OsisTempDataCollection).process(); x()



    /***************************************************************************/
    /* If you do need to revert to doing stuff in USX, you'll probably want
       analogues of the following, but you will probably need to write stuff
       specifically for USX. */

    handleReversification(); x()
    removeDummyVerses(OsisTempDataCollection)
    ContentValidator.process(OsisTempDataCollection, Osis_FileProtocol, OsisPhase2SavedDataCollection, Osis_FileProtocol)
    EmptyVerseHandler.preventSuppressionOfEmptyVerses(OsisTempDataCollection)
    EmptyVerseHandler(OsisTempDataCollection).markVersesWhichWereEmptyInTheRawText()
    SE_FeatureCollector(OsisTempDataCollection).process()
    ProtocolConverterInternalOsisToOsisWhichOsis2modCanUse.process(OsisTempDataCollection)
    Dom.outputDomAsXml(doc, FileLocations.getTempOsisFilePath(), null)
  }


  /***************************************************************************/
  /* Time to worry about reversification.  First off, we need to read the
     data and determine how far adrift of NRSVA we are, so we can decide what
     kind of reversification (if any) might be appropriate. */

  private fun handleReversification ()
  {
    ReversificationData.process(OsisTempDataCollection)
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
  /* Earlier processing -- here or in things it relies upon -- may have
     introduced dummy verses.  We now remove them. */

  private fun removeDummyVerses (dataCollection: X_DataCollection)
  {
    Dbg.reportProgress("Removing any dummy verses.")
    dataCollection.getRootNodes().forEach {rootNode ->
      Dom.findNodesByName(rootNode, dataCollection.getFileProtocol().tagName_verse(), false).filter { NodeMarker.hasDummy(it) }.forEach { Dom.deleteNode(it) }
      Dom.setNodeName(rootNode, "div")
    }
  }


  /****************************************************************************/
  /* Sets up the input and working data.

     On entry, Phase1TextOutput contains OSIS.  It will be valid in the sense
     that it contains only official OSIS tags and attributes (or OSIS
     extensions where those are permitted).  But it may lack verse eids, or
     if it has them, they may not be optimally positioned.

     (It may also fail to conform to the OSIS XSD in other respects too,
     because -- as explained elsewhere -- there are certain aspects of that
     which we have ended up concluding are just too difficult to live with.)

     As regards the issues with verse ends, we have two different situations
     to consider:

     - This run may have started from VL or USX, in which case I won't yet
       have generated any verse eids.

     - Or the run may have started from OSIS.  This may be third party OSIS,
       or it may be our own OSIS from a previous run.  Either way, I have had
       no control over it, and although it will probably contain eids (or else
       it would be totally invalid), there is no guarantee they are placed in
       such a way to minimise cross-boundary-markup, which is what I want.


     Regardless of the source, therefore, I remove any existing verse eids,
     and then insert new ones optimally positioned, to the extent that I can
     achieve it.  (But before I can do that, I also need to sort out tables,
     because these will normally fall foul of cross-boundary markup, and
     will complicate verse-end placement.)

     If the run started from VL or USX, I will in due course save this
     improved OSIS to InputOsis to act as a possible input for future runs
     (for example where someone finds it convenient to tweak OSIS in order to
     add tagging or whatever).  I _don't_ save it if the starting point was
     itself OSIS, because I assume we want to retain that version.

     Further processing has to be applied to the OSIS before we are in a
     position to create a module, but I do that to a separate throw-away copy
     which is held in OsisTempDataCollection. */

  private fun setupInitialData ()
  {
    /**************************************************************************/
    OsisTempDataCollection.loadFromText(Phase1TextOutput, false)
    Phase1TextOutput = ""



    /**************************************************************************/
    /* OsisTempDataCollection is now as correct as it's going to get in raw
       form, and we need to hang on to the content in case we need to save it
       later to form a possible input to future processing.  I can't work out
       what would be needed to copy from one data collection to another, so
       rather than copy, I load the OsisTempDataCollection document into
       OsisPhase2SavedDataCollection, which will do the necessary. */

    OsisPhase2SavedDataCollection.loadFromDocs(listOf(OsisTempDataCollection.getDocument()))



    /**************************************************************************/
    /* Apply temporary (or permanent) changes to make the data more amenable to
       processing. */

    ProtocolConverterOsisForThirdPartiesToInternalOsis.process(OsisTempDataCollection)



    /**************************************************************************/
    /* Future reference processing -- particularly any processing which
       requires us to expand out elisions -- needs to be based upon the
       Bible structure associated with OsisTempDataCollection. */

    RefBase.setBibleStructure(OsisTempDataCollection.BibleStructure)
  }
}