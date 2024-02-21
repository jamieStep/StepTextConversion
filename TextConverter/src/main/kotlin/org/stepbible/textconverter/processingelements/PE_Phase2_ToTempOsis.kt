package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.osisinputonly.Osis_CanonicalHeadingsHandler
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
  override fun pre () { StepFileUtils.clearFolder(FileLocations.getOutputFolderPath()) }
  override fun process () = doIt()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Be very careful if you intend to change the order of things below (although
     hopefully you can't go too far wrong, because the various SE processors
     all check that the relevant things have run before they do).

     As regards the input ...

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

  private fun doIt ()
  {
    /***************************************************************************/
    fun x () = Logger.announceAllAndTerminateImmediatelyIfErrors()



    /***************************************************************************/
    /* Pick up the input data. */

    StepFileUtils.createFolderStructure(FileLocations.getTempOsisFolderPath())
    OsisTempDataCollection.loadFromText(Phase1TextOutput, false)
    RefBase.setBibleStructure(OsisTempDataCollection.getBibleStructure())
    val doc = OsisTempDataCollection.getDocument()
    DataCollection = OsisTempDataCollection
    Phase1TextOutput = ""



    /***************************************************************************/
    /* We may or may not have verse ends in the data.  We definitely need them
       though, so I first delete any that we already have (to get things to a
       common state), and then insert them in an acceptable (but probably non-
       optimal) position.

       Naturally I'd _prefer_ them to be located optimally (by which I mean in
       places which avoid cross-boundary markup).  However, to achieve that
       I have to do certain fairly unpalatable things to the OSIS (replacing
       semantic markup by formatting markup etc), and we need to have OSIS
       which we can pass to third parties where appropriate, and I assume the
       third parties would rather have the semantic information available.

       So, the verse-end placement here would be good enough to create valid
       OSIS without losing semantic information.  I do better later on, once
       we've taken a copy of the data with a possible view to making it
       available to third parties, or using it ourselves as input on future
       runs.

       Having applied these tweaks, I then save the OSIS (or I do if we weren't
       starting from OSIS in the first place), but under a temporary name just
       in case later processing fails.

       I use originalDataCollection here to retain a copy of the original data,
       because I need it later for validation. */

    SE_VerseEndRemover(OsisTempDataCollection).processAllRootNodes()
    val originalDataCollection = Osis_DataCollection()
    originalDataCollection.loadFromDocs(listOf(OsisTempDataCollection.getDocument()))
    originalDataCollection.copyProcessRegistryFrom(OsisTempDataCollection)

    if ("osis" != ConfigData["stepOriginData"]!!)
    {
      StepFileUtils.clearFolder(FileLocations.getInputOsisFolderPath())
      SE_BasicVerseEndInserter(originalDataCollection).processAllRootNodes(); x()
      Dom.outputDomAsXml(originalDataCollection.getDocument(), FileLocations.getInputOsisFilePathTemp(), null)
    }



    /**************************************************************************/
    /* I start here with changes that everything else relies on, and then
       continue with any other changes which are easy because they don't rely
       on anything much and / or don't markedly affect other processing. */

    ProtocolConverterOsisForThirdPartiesToInternalOsis.process(OsisTempDataCollection); x() // Minor changes to make processing easier (some of which may have to be undone later).
    SE_StrongsHandler(OsisTempDataCollection).processAllRootNodes(); x()                    // Canonicalises Strong's references.



    /**************************************************************************/
    /* Things concerned with possibly significant restructuring of the text. */

    SE_TableHandler(OsisTempDataCollection).processAllRootNodes(); x()                      // Collapses tables which span verses into a single elided verse.
    SE_ElisionHandler(OsisTempDataCollection).processAllRootNodes(); x()                    // Expands elisions out into individual verses.
    Osis_CanonicalHeadingsHandler(OsisTempDataCollection).process(); x()                    // Simplifies canonical headings to reduce the chance of cross-boundary markup.
    SE_EnhancedVerseEndInsertionPreparer(OsisTempDataCollection).processAllRootNodes(); x() // Continues the work of SE_CanonicalHeadingsHandler, making things easier to insert verse ends..
    SE_EnhancedVerseEndInserter(OsisTempDataCollection).processAllRootNodes(); x()          // Position verse ends so as to reduce the chances of cross-boundary markup.
    SE_SubverseCollapser(OsisTempDataCollection).processAllRootNodes(); x()                 // Collapses subverses into the owning verses, if that's what we're doing.
    OsisTempDataCollection.reloadBibleStructureFromRootNodes(wantWordCount = false); x()    // Probably a good idea to build / rebuild the structure here.



    /**************************************************************************/
    SE_BasicValidator(OsisTempDataCollection).structuralValidation(OsisTempDataCollection)  // Checks for basic things like all verses being under chapters.
    SE_ListEncapsulator(OsisTempDataCollection).processAllRootNodes(); x()                  // Might encapsulate lists (but in fact does not do so currently).
    Osis_CrossReferenceChecker.process(OsisTempDataCollection); x()                         // Checks for invalid cross-references, or cross-references which point to non-existent places.
    handleReversification(); x()                                                            // Does what it says on the tin.
    SE_CalloutStandardiser(OsisTempDataCollection).processAllRootNodes(); x()               // Force callouts to be in house style, assuming that's what we want.
    removeDummyVerses(OsisTempDataCollection)                                               // Ditto.
    ContentValidator.process(OsisTempDataCollection, Osis_FileProtocol, originalDataCollection, Osis_FileProtocol) // Checks current canonical content against original.
    EmptyVerseHandler.preventSuppressionOfEmptyVerses(OsisTempDataCollection)               // Unless we take steps, empty verses tend to be suppressed when rendered.
    EmptyVerseHandler(OsisTempDataCollection).markVersesWhichWereEmptyInTheRawText()        // Back to doing what it says on the tin.
    SE_FeatureCollector(OsisTempDataCollection).processAllRootNodes()                       // Gather up information which might be useful to someone administering texts.
    ProtocolConverterInternalOsisToOsisWhichOsis2modCanUse.process(OsisTempDataCollection)  // Converts what we have back to something close enough to standard OSIS to be reasonably confident osis2mod can handle it.
    Dom.outputDomAsXml(doc, FileLocations.getTempOsisFilePath(), null)           // Save the OSIS so it's available to osis2mod.
  }


  /***************************************************************************/
  /* Time to worry about reversification.  First off, we need to read the
     data and determine how far adrift of NRSVA we are, so we can decide what
     kind of reversification (if any) might be appropriate. */

  private fun handleReversification ()
  {
    ReversificationData.process(OsisTempDataCollection)
    Osis_DetermineReversificationTypeEtc.process(OsisTempDataCollection.getBibleStructure())

    when (ConfigData["stepReversificationType"]!!.lowercase())
    {
      "runtime" ->
      {
        SE_RuntimeReversificationHandler(OsisTempDataCollection).processAllRootNodes()
      }

      "conversiontime" ->
      {
        Osis_SE_ConversiontimeReversification(OsisTempDataCollection).processDataCollection()
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
}