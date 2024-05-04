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

object PE_Phase2_ToInternalOsis : PE
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
    fun addComment (doc: Document, text: String) = Dom.insertNodeBefore(doc.documentElement.firstChild, Dom.createCommentNode(doc, " $text "))



    /***************************************************************************/
    fun x () = Logger.announceAllAndTerminateImmediatelyIfErrors()



    /***************************************************************************/
    /* Pick up the input data. */

    StepFileUtils.createFolderStructure(FileLocations.getInternalOsisFolderPath())
    InternalOsisDataCollection.loadFromText(Phase1TextOutput)
    RefBase.setBibleStructure(InternalOsisDataCollection.getBibleStructure()) // Needed to cater for the possible requirement to expand ranges.
    val doc = InternalOsisDataCollection.getDocument()
    Phase1TextOutput = ""
    //Dbg.d(doc)



    /***************************************************************************/
    /* We may or may not have verse ends in the data.  We definitely need them
       though.

       I first delete any that we already have (to get things to a common
       state).

       I'm going to need them in both the externally-facing and the internally-
       facing OSIS.

       As regards the former, I want to do absolutely minimal work to it, so I
       simply place verse ends immediately before the following verse start.

       As regards the internally-facing OSIS, I need to place the verse ends
       rather more carefully with a view to avoiding cross-boundary markup.
       This I defer for a moment -- see the lines starting SE_EnhancedVerseEnd
       below.

       At this point, I've done everything I'm going to do to the externally-
       facing OSIS and on runs which are starting from VL or USX, I'm going to
       need to save it in case we want to apply manual tweaks to it in future
       and then use it as input.  I don't save it until much later in the
       processing, in case anything goes wrong -- otherwise people might find
       a saved version lying around and automatically assume it's ok. */

    SE_VerseEndRemover(InternalOsisDataCollection).processAllRootNodes()
    ExternalOsisDataCollection.loadFromDocs(listOf(InternalOsisDataCollection.getDocument()))
    ExternalOsisDataCollection.copyProcessRegistryFrom(InternalOsisDataCollection)

    if ("osis" != ConfigData["stepOriginData"]!!)
    {
      StepFileUtils.clearFolder(FileLocations.getInputOsisFolderPath())
      SE_BasicVerseEndInserter(ExternalOsisDataCollection).processAllRootNodes(); x()
      addComment(ExternalOsisDataCollection.getDocument(), "Externally-facing OSIS")
    }



    /**************************************************************************/
    /* I start here with changes that everything else relies on, and then
       continue with any other changes which are easy because they don't rely
       on anything much and / or don't markedly affect other processing. */

    addComment(doc, "Internally-facing OSIS")
    ProtocolConverterOsisForThirdPartiesToInternalOsis.process(InternalOsisDataCollection); x() // Minor changes to make processing easier (some of which may have to be undone later).
    SE_StrongsHandler(InternalOsisDataCollection).processAllRootNodes(); x()                    // Canonicalises Strong's references.



    /**************************************************************************/
    /* Things concerned with possibly significant restructuring of the text. */

    SE_TableHandler(InternalOsisDataCollection).processAllRootNodes(); x()                      // Collapses tables which span verses into a single elided verse.
    SE_ElisionHandler(InternalOsisDataCollection).processAllRootNodes(); x()                    // Expands elisions out into individual verses.
    SE_EnhancedVerseEndInsertionPreparer(InternalOsisDataCollection).processAllRootNodes(); x() // Continues the work of SE_CanonicalHeadingsHandler, making things easier to insert verse ends.
    SE_EnhancedVerseEndInserter(InternalOsisDataCollection).processAllRootNodes(); x()          // Position verse ends so as to reduce the chances of cross-boundary markup.
    InternalOsisDataCollection.reloadBibleStructureFromRootNodes(wantWordCount = false); x()    // Probably a good idea to build / rebuild the structure here.



    /**************************************************************************/
    SE_BasicValidator(InternalOsisDataCollection).structuralValidation(InternalOsisDataCollection)  // Checks for basic things like all verses being under chapters.
    SE_ListEncapsulator(InternalOsisDataCollection).processAllRootNodes(); x()                      // Might encapsulate lists (but in fact does not do so currently).
    Osis_CrossReferenceChecker.process(InternalOsisDataCollection); x()                             // Checks for invalid cross-references, or cross-references which point to non-existent places.
    handleReversification(); x()                                                                    // Does what it says on the tin.
    // $$$ SE_SubverseCollapser(InternalOsisDataCollection).processAllRootNodes(); x()              // Collapses subverses into the owning verses, if that's what we're doing (mainly or exclusively on public modules).
    SE_CalloutStandardiser(InternalOsisDataCollection).processAllRootNodes(); x()                   // Force callouts to be in house style, assuming that's what we want.
    removeDummyVerses(InternalOsisDataCollection); x()                                              // Ditto.
    ContentValidator.process(InternalOsisDataCollection, Osis_FileProtocol, ExternalOsisDataCollection, Osis_FileProtocol); x() // Checks current canonical content against original.
    EmptyVerseHandler.preventSuppressionOfEmptyVerses(InternalOsisDataCollection); x()              // Unless we take steps, empty verses tend to be suppressed when rendered.
    EmptyVerseHandler(InternalOsisDataCollection).markVersesWhichWereEmptyInTheRawText(); x()       // Back to doing what it says on the tin.
    SE_TextAnalyser(InternalOsisDataCollection).processAllRootNodes(); x()                          // Gather up information which might be useful to someone administering texts.
    SE_LastDitchTidier(InternalOsisDataCollection).processAllRootNodes(); x()                       // Ad hoc last minute tidying.
    ProtocolConverterInternalOsisToOsisWhichOsis2modCanUse.process(InternalOsisDataCollection); x() // Converts what we have back to something close enough to standard OSIS to be reasonably confident osis2mod can handle it.
    Dom.outputDomAsXml(InternalOsisDataCollection.getDocument(), FileLocations.getInternalOsisFilePath(), null)
                                                                                                    // Save the OSIS so it's available to osis2mod.
  }


  /***************************************************************************/
  /* Time to worry about reversification.  First off, we need to read the
     data and determine how far adrift of NRSVA we are, so we can decide what
     kind of reversification (if any) might be appropriate. */

  private fun handleReversification ()
  {
    //Dbg.d(InternalOsisDataCollection.getDocument())
    ReversificationData.process(InternalOsisDataCollection)
    Osis_DetermineReversificationTypeEtc.process(InternalOsisDataCollection.getBibleStructure())

    Osis_CanonicalHeadingsHandler(InternalOsisDataCollection).process()

    when (ConfigData["stepReversificationType"]!!.lowercase())
    {
      "runtime" ->
      {
        SE_RuntimeReversificationHandler(InternalOsisDataCollection).processAllRootNodes()
      }

      "conversiontime" ->
      {
        Osis_SE_ConversiontimeReversification(InternalOsisDataCollection).processDataCollection()
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