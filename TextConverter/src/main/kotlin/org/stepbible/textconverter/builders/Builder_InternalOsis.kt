package org.stepbible.textconverter.builders

import org.stepbible.textconverter.osisonly.*
import org.stepbible.textconverter.protocolagnosticutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefBase
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/******************************************************************************/
/**
 * Converts the initial form of OSIS into the form needed in order to generate
 * the module.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_InternalOsis: Builder()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Converting OSIS to version required for Sword module."
  override fun commandLineOptions () = null


  /****************************************************************************/
  /* Be very careful if you intend to change the order of things below.

     As regards the input ...

     On entry, ExternalOsisDoc contains OSIS which I regard as being suitable
     for retention as the external-facing OSIS.

     It is guaranteed to use <chapter> rather than div:chapter, and will
     use milestone verses.  If we are starting from OSIS, those eids will
     be as placed in the original OSIS, unless the original OSIS had none,
     in which case they will be positioned immediately before the next sid.
  */
  /****************************************************************************/


  /****************************************************************************/
  override fun doIt ()
  {
    /***************************************************************************/
    Builder_InitialOsisRepresentationOfInputs.process()
    Rpt.report(0, banner())



    /***************************************************************************/
    fun x () = Logger.announceAllAndTerminateImmediatelyIfErrors() // A little shorthand.
    val notesArchiver = PA_ElementArchiver()



    /***************************************************************************/
    /* Create a home for what we're about to generate. */

    //Dbg.outputText(Phase1TextOutput)
    StepFileUtils.deleteFolder(FileLocations.getInternalOsisFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getInternalOsisFolderPath())



    /***************************************************************************/
    /* There are a few things which either delete nodes from the structure
       altogether, or archive them for reinstatement later.  Both of these
       kinds of changes reduce the number of nodes to be processed, so
       probably better to make them as early as possible. */

    InternalOsisDataCollection.loadFromDoc(ExternalOsisDoc); x()
    notesArchiver.archiveElements(InternalOsisDataCollection, InternalOsisDataCollection.getFileProtocol()::isNoteNode); x()
                                                                                         // Hive off footnotes to separate document to speed up main processing.
    Osis_ChapterAndVerseStructurePreprocessor.process(InternalOsisDataCollection); x()   // Sort out chapter structure, insert missing chapters, etc.
    PA_VerseEndRemover.process(InternalOsisDataCollection); x()                          // Some inputs may have eids already, some not.  Reduce everything to a common state.
    PA_DummyVerseHandler.insertDummyVerses(InternalOsisDataCollection)



    /***************************************************************************/
    val doThisBitInParallel = true
    val warningAboutInterleaving = if (ParallelRunning.isPermitted() && doThisBitInParallel) " (Reporting between now and 'Finished loading and evaluating data' may be interleaved and may therefore look confusing.)" else ""
    Rpt.report(level = 0, "Loading and evaluating data.$warningAboutInterleaving")
    m_OriginalOsisDataCollection = Osis_DataCollection()
    m_OriginalOsisDataCollection.loadFromRootNodes(InternalOsisDataCollection); x() // Make sure this happens _before_ the parallel processing.

    with(ParallelRunning(doThisBitInParallel)) {
      run {
        asyncable { RefBase.setBibleStructure(InternalOsisDataCollection.getBibleStructure()); x() }

        asyncable {
          PA_BasicVerseEndInserter.process(m_OriginalOsisDataCollection)
          PA_DummyVerseHandler.removeDummyVerses(m_OriginalOsisDataCollection)
        }
      }
    }

    if (warningAboutInterleaving.isNotEmpty())
      Rpt.report(level = 1, "Finished loading and evaluating data.")



    /**************************************************************************/
    Rpt.report(level = 0, "Handling Strongs, tables and elisions.")
    PA_StrongsHandler.process(InternalOsisDataCollection); x()   // Canonicalise Strong's markup.
    Osis_BasicTweaker.process(InternalOsisDataCollection); x()   // Minor changes to make processing easier.
    PA_TableHandler.process(InternalOsisDataCollection); x()     // Collapses tables which span verses into a single elided verse.
    PA_ElisionHandler.process(InternalOsisDataCollection); x()   // Expands elisions out into individual verses.

    
    
    /**************************************************************************/
    /* Time to put the eids back again, but hopefully in a better location. */
    
    Rpt.report(level = 0, "Reinserting eids.")
    PA_EnhancedVerseEndInsertionPreparer.process(InternalOsisDataCollection); x() // Relatively simple and hopefully uncontentious tweaks to make it easier to insert verse ends.
    PA_EnhancedVerseEndInserter.process(InternalOsisDataCollection); x()          // Positions verse ends so as to reduce the chances of cross-boundary markup.
    
    
    
    /**************************************************************************/
    /* Standardise canonical heading details. */

    Rpt.report(level = 0, "Handling canonical headings and lists.")
    PA_CanonicalHeadingsHandler.process(InternalOsisDataCollection); x()  // Canonicalises canonical headings, as it were.
    PA_ListEncapsulator.process(InternalOsisDataCollection); x()          // Might encapsulate lists (but in fact does not do so currently).


    
    /**************************************************************************/
    /* It's probably a good idea to build / rebuild the structure details. */
    
    InternalOsisDataCollection.reloadBibleStructureFromRootNodes(wantCanonicalTextSize = true); x()
                                                             


    /**************************************************************************/
    Rpt.report(level = 0, "Performing initial validation.")
    Osis_BasicValidator.structuralValidation(InternalOsisDataCollection); x()            // Checks for basic things like all verses being under chapters.



    /**************************************************************************/
    Rpt.report(level = 0, "Performing reversification if necessary.")
    Osis_DetermineReversificationTypeEtc.process()
    PA_ReversificationHandler.instance().process(InternalOsisDataCollection); x()



    /**************************************************************************/
    Rpt.report(level = 0, "Performing validation.")
    Osis_BasicValidator.structuralValidationAndCorrection(InternalOsisDataCollection); x() // Structural checks.  May create empty verses if necessary.



    /**************************************************************************/
    /* We have to collapse subverses into the owning verse, because osis2mod /
       JSword can't cope with subverses.  However, it has to come _after_
       reversification, because that may, itself, do things with subverses, and
       we don't want to tread upon its toes.  And it also has to come after
       final validation in case the change in structure confuses things. */

    PA_DummyVerseHandler.removeDummyVerses(InternalOsisDataCollection); x() // Earlier processing will have introduced dummy verses at the ends of chapters.  Get rid of them.
    PA_SubverseCollapser.process(InternalOsisDataCollection); x()           // Collapses subverses into the owning verses.



    /**************************************************************************/
    PA_ContentValidator.process(InternalOsisDataCollection, m_OriginalOsisDataCollection); x()  // Checks current canonical content against original.
    m_OriginalOsisDataCollection.clearAll()                                                     // Frees up memory and prevent inadvertent further use.



    /**************************************************************************/
    Rpt.report(level = 0, "Yet more tidying.")
    PA_EmptyVerseHandler(InternalOsisDataCollection.getFileProtocol()).markVersesWhichWereEmptyInTheRawText(InternalOsisDataCollection); x() // What it says on the tin.
    PA_EmptyVerseHandler.preventSuppressionOfEmptyVerses(InternalOsisDataCollection); x()  // Unless we take steps, empty verses tend to be suppressed when rendered.
    PA_TextAnalyser.process(InternalOsisDataCollection)  ; x()                             // Gathers up information which might be useful to someone administering texts.
    Osis_FinalInternalOsisTidier.process(InternalOsisDataCollection, notesArchiver); x()   // Ad hoc last minute tidying.
    PA_CalloutStandardiser.process(InternalOsisDataCollection); x()                        // Forces callouts to be in house style, assuming that's what we want.
    Osis_CrossReferenceChecker.process(InternalOsisDataCollection)                         // Does what it says on the tin.
    Osis_BasicTweaker.unprocess(InternalOsisDataCollection); x()                           // Undoes any temporary tweaks which were applied to make the overall processing easier.
    PA_FinalValidator.process(InternalOsisDataCollection); x()                             // Final health checks.
    Osis_InternalTagReplacer.process(InternalOsisDataCollection); x()                      // Replaces any _X_ tags which I introduced with pukka OSIS ones.
    PA_TemporaryAttributeRemover.process(InternalOsisDataCollection); x()                  // Removes all temporary attributes.



    /**************************************************************************/
    /* Save the OSIS so it's available to osis2mod.

       We have two options here.

       The more normal one appears in the else clause, and writes the internal
       OSIS to the output file.

       In this respect, I don't really like the final code fragment (^lt etc)
       below.  It's there to undo temporary changes to text content which may
       have been applied much earlier in the processing (in this case, to replace
       things like '&lt;', because many transformations applied here start
       getting confused as to whether we have '&lt;' or '<', the latter being a
       problem in some cases).  But this architecture means that we apply these
       changes somewhere miles away in the code (in X_DataCollection), and then
       undo them again here, which isn't ideal.

       Note, incidentally, that this is different from BasicOsisTweaker.unprocess.
       That applies changes to the DOM.  Here we are applying changes to the XML
       text to which the DOM is converted.  Hence, unfortunately, it's not
       possible to combine the two steps into one.

       I said there were two options.  The other ignores all the work done thus
       far in creating an internal DOM representation, and simply assumes (where
       starting from OSIS) that the original OSIS was ok.  I have added this
       option because with ESV we do start from OSIS, and there was some concern
       that we had a version of ESV which was apparently working, and didn't
       want to risk introducing errors into it.

       If used commonly, this is a somewhat risky undertaking, because I don't
       do things to the internal OSIS for the sake of it: for instance, I make
       sure there are no holes in the versification scheme, and I also apply
       tweaks which are needed to ensure the rendering looks ok.  If we don't
       use the internal OSIS, any such tweaks are lost.

       Note that I still need to generate the internal OSIS, even if I then
       throw it away, because one of the side-effects of doing that is that I
       generate the supporting information needed for the various configuration
       files (the Sword configuration file, the samification JSON file, etc).
       This highlights another potential issue, in that this data will be based
       upon the _internal_ OSIS.  We will now be replacing that internal OSIS
       with the original OSIS, so we have to assume that the two are
       sufficiently similar that the supporting information remains relevant.
       This is definitely not a foregone conclusion. */

    ConfigData.makeBibleDescriptionAsItAppearsOnBibleList(InternalOsisDataCollection.getBookNumbers())

    if ("asoutput" == ConfigData["stepUseExistingOsis"]?.lowercase())
    {
      Rpt.report(level = 1, "Writing version of OSIS needed for use with osis2mod.")
      StepFileUtils.copyFile(FileLocations.getInternalOsisFilePath(), FileLocations.getInputOsisFilePath()!!)
    }
    else
    {
      val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
      val comment = "This OSIS file created by the STEPBible project $timeStamp.  This is a throw-away file.  Use the external OSIS as the basis of any long-term changes you wish to make."
      Rpt.report(level = 1, "Writing version of OSIS needed for use with osis2mod.")
      Dom.outputDomAsXml(InternalOsisDataCollection.convertToDoc(),
                         FileLocations.getInternalOsisFilePath(),
                         comment) { x -> x.replace("^lt;", "&lt;")
                                          .replace("^gt;", "&gt;")
                                          .replace("xmlns=\"\" ", "") }
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private lateinit var m_OriginalOsisDataCollection: Osis_DataCollection
}
