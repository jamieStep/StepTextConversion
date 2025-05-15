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
 *
 *
 *
 *
 * ## Preamble
 *
 * The raw data may have been supplied in any of a number of different formats.
 * By the time we reach here, all of these will have been converted to an initial
 * OSIS representation, as close as possible to the original.  It is the task
 * of this present class and the things upon which it relies to convert this
 * initial OSIS into two separate forms -- a) a form which I refer to here as
 * *internal* OSIS, and b) a form which, unsurprisingly, I refer to as
 * *external* OSIS.
 *
 * External OSIS is the more straightforward of the two.  It is pretty much a
 * direct copy of the OSIS received here as input, with only such minimal
 * changes as may be necessary to render it usable either by third parties
 * (should we wish to make it available), or by us if we decide in future that
 * -- for example -- we wish to add tagging, and choose the OSIS representation
 * as the more suitable for this purpose.
 *
 * Internal OSIS has much more processing applied to it -- for example, to
 * fill in gaps in the versification, to apply reversification processing,
 * to make ad hoc changes needed to work around downstream rendering issues,
 * etc.  In theory internal OSIS is throw-away, to be retained only until the
 * module itself has been generated, although I do tend to keep it lying around
 * for debug purposes.
 *
 *
 *
 *
 *
 * ## The processing environment
 *
 * There are a few ad hoc things about the processing environment which you need
 * to be aware of if the rest of this is to make sense:
 *
 * - The two main standards governing the inputs we receive -- USX and OSIS --
 *   are complicated enough that people tend to differ in their interpretation
 *   of them.  As a result, it is impossible to be sure that the converter will
 *   always be able to handle new texts appropriately: quite often it is
 *   necessary to tweak it to address unexpected new situations.
 *
 * - Taking into account the flexibility inherent in the standards and the fact
 *   that -- particularly in the case of USX -- important aspects of the
 *   standard have changed over time, there are some particular complications
 *   which may need to be taken into account.
 *
 * - Primary amongst these is the fact that some versions of the standards used
 *   milestone markers to indicate the *start* of verses, but did not require
 *   the *end* of verses to be marked at all; while others relied upon enclosing
 *   tags for verses.  In fact pretty much all USX and OSIS texts which we
 *   encounter now see to have milestones for start and end.  I have retained
 *   processing capable of addressing the texts which do not, but do not
 *   anticipate it being called, and am therefore no longer confirming that it
 *   continues to work in the face of any other changes.
 *
 * - A long while back, we anticipated that reversification would entail
 *   physically restructuring texts by moving words around from one place to
 *   another.  This meant that it was important to do our best to avoid having
 *   markup running across verse boundaries (something which the standards, with
 *   their milestone markers, rather encourage) -- and so I went to some lengths
 *   to avoid this.  This is no longer necessary, because we no longer intend to
 *   restructure texts in this way.
 *
 * - In fact, even without reversification, cross-boundary markup is a problem
 *   for things like interlinear display, and apparently osis2mod itself may
 *   restructure material to avoid it.  The fact the osis2mod deals with it
 *   kind of means that we don't have to worry about it.  Except that I think
 *   there are some cases where it looks as though we do, because osis2mod
 *   issues error messages.  Primary amongst these are tables, where it is
 *   almost inevitable that markup will run across boundaries, and here I
 *   *do* intervene, converting tables to elisions.
 *
 * - osis2mod uses an internal data structure in which individual verses are not
 *   labelled -- instead they occupy particular slots in an overall structure,
 *   and it relies upon slot n corresponding to verse n.  This has two
 *   particular corollaries as far as we are concerned.  First, we cannot have
 *   any holes in the versification scheme.  If the text we receive jumps
 *   straight from Gen 1:1 to Gen 1:3, we need to create an empty Gen 1:2 as a
 *   placeholder.  And second, we cannot have subverses -- osis2mod has no way
 *   of recognising them.  This means that if the incoming text contains
 *   subverses, we have to collapse them into the owning verse before passing
 *   the data to osis2mod.
 *
 *
 *
 *
 *
 * ## General notes on processing
 *
 * In general it is probably better to rely upon in-code comments below to
 * explain what is going on.  However, there are a few aspects of the processing
 * which are probably best covered in overview.
 *
 * Primary amongst these is the matter of reversification, and how this needs to
 * sit in relation to other changes which have to be applied to the text --
 * notably elisions, collapsed subverses, and empty verses generated to fill in
 * holes in the text.
 *
 * First off, just to say that I am not concerned here with things which have
 * already been done to the raw data.  If the translators have elided a number
 * of verses, then that is what it is, and there's nothing we can do about it.
 * The concern here is the things which _we_ need to do, and the order in which
 * we need to do them.
 *
 * - Elisions: Here I have to say I don't really know what to do for the best.
 *   I am most likely to need to add elisions where the raw data contains
 *   tables, and I am likely to need to do it because there is some (admittedly
 *   sketchy) evidence that osis2mod does not cope well with tables which run
 *   across verse boundaries.  If I do it early, reversification will see a
 *   number of empty verses and one huge one, which may not fit its
 *   expectations.  If I do it late, it may generate mappings which fit what
 *   it sees, but then the data I actually supply to osis2mod will be
 *   different from that because I will be changing the data after osis2mod
 *   has seen it.  On balance I think probably doing it early is better.
 *
 * - Missing verses: Here I'm inclined to do it early.  If verses are missing,
 *   we need to fill them in at some point before passing data to osis2mod,
 *   and doing it early means they will be there for osis2mod to detect
 *   and handle them.
 *
 * - Collapsed subverses: This I think is best handled late, but with the
 *   proviso that it will have implications for the reversification
 *   processing.  If I leave the subverses in place up to and including
 *   any reversification processing, then reversification will be able to
 *   add footnotes as appropriate.  Before I pass the data to osis2mod,
 *   though, I do have to collapse the subverses, and this means that at
 *   that point they will no longer be in the text.  My belief was that
 *   I should therefore remove any mappings which mentioned subverses as
 *   source.  However, at present I'm being told to leave all mappings in
 *   place, regardless.
 *
 *
 *
 *
 * ## Historical note
 *
 * In previous versions of this code, I was doing quite a lot of restructuring.
 * This was partly in anticipation of a possible requirement to handle what I
 * used to refer to as 'conversion time reversification', which entailed
 * physically restructuring the text and therefore could not cope well with
 * cross-verse-boundary markup; and partly because we had prima facie
 * evidence that osis2mod cannot itself handle some forms of cross-boundary
 * markup.
 *
 * With conversion time reversification off the agenda, that reduces the
 * requirement for such restructuring, and so -- with a couple of
 * exceptions I no longer apply much by way of restructuring.  The
 * exceptions are:
 *
 * - Turning tables into elisions.
 *
 * - Expanding elisions out into a number of contiguous empty verses and
 *   one large verse which contains the full text.
 *
 *
 * Or at least, I *say* I am no longer restructuring; it is possible, in fact,
 * that I have missed some examples of this, so if you hit bugs it's worth
 * checking for that.
 *
 * Since I am no longer doing much by way of restructuring, I have also now
 * commented out calls below which were involved in comparing the finished
 * text with the input text to check that the verse content had not altered.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_InternalOsis: Builder(), ObjectInterface
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

     After the call to Builder_InitialOsisRepresentationOfInputs.process(),
     ExternalOsisDoc contains OSIS which I regard as requiring few if any
     changes to make it suitable for retention as the external-facing OSIS.

     It is guaranteed to use <chapter> rather than div:chapter, and (in
     contrast to earlier implementations), I assume that it will have milestone
     verse tags, with eids as well as sids, and that the eids will have been
     put in 'reasonable' places.
  */
  /****************************************************************************/


  /****************************************************************************/
  /* We have two options here.  In most cases, we take the input OSIS merely as
     a starting point, and do a lot to it to cater for reversification, etc.
     This is the more normal option.

     The alternative -- which has not really been exercised much, and
     therefore may require some additional work, is to bypass all of this and
     simply use the input OSIS _exactly_ as-is.  This option was introduced
     where we already had a text which we trusted, and didn't want to risk the
     converter messing it up.  However, at present it is bypassing _everything_
     (including identifying data which would give rise to mappings in the
     osis2mod JSON file), and this is probably too much (particularly since it
     also bypasses the processing which fills in holes in the text).

     I don't really like the final code fragment (^lt etc) below.  It's there to
     undo temporary changes to text content which may have been applied much
     earlier in the processing (in this case, to replace things like '&lt;',
     because many transformations applied here start getting confused as to
     whether we have '&lt;' or '<', the latter being a problem in some cases).
     But this architecture means that we apply these changes somewhere miles
     away in the code (in X_DataCollection), and then undo them again here, which
     isn't ideal.

     Note, incidentally, that this is different from BasicOsisTweaker.unprocess.
     That applies changes to the DOM.  Here we are applying changes to the XML
     text to which the DOM is converted.  Hence, unfortunately, it's not
     possible to combine the two steps into one. */

  override fun doIt ()
  {
    /***************************************************************************/
    /* Create a home for what we're about to generate. */

    StepFileUtils.deleteFileOrFolder(FileLocations.getOutputFolderPath())
    //StepFileUtils.deleteFolder(FileLocations.getInternalOsisFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getInternalOsisFolderPath())



    /***************************************************************************/
    /* Certain parameters need to be driven by things like what target audience
       we have in mind (public or STEP-only).  Best to work these out as early
       as possible. */

    Osis_AudienceAndCopyrightSpecificProcessingHandler.process()



    /**************************************************************************/
    if ("asis" == ConfigData["stepUseExistingOsis"]?.lowercase())
    {
      Rpt.report(level = 0, "Using existing OSIS as-is.")
      val doc = Dom.getDocument(FileLocations.getInputOsisFilePath()!!)
      BookOrdering.initialiseFromOsis(doc)
      InternalOsisDataCollection.loadFromDoc(doc)

      Rpt.report(level = 0, "Performing reversification if necessary.")
      Osis_AudienceAndCopyrightSpecificProcessingHandler.doReversificationIfNecessary(InternalOsisDataCollection)
    }
    else
      doIt1()



    /**************************************************************************/
    //ConfigData.makeStepTextDescriptionAsItAppearsOnBibleList(InternalOsisDataCollection.getBookNumbers())
    val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    val comment = "This OSIS file created by the STEPBible project $timeStamp.  This is a throw-away file.  Use the external OSIS as the basis of any long-term changes you wish to make."
    Rpt.report(level = 1, "Writing version of OSIS needed for use with osis2mod.")
    Dom.outputDomAsXml(InternalOsisDataCollection.convertToDoc(),
                       FileLocations.getInternalOsisFilePath(),
                       comment) { x -> x.replace("^lt;", "&lt;")
                                        .replace("^gt;", "&gt;")
                                        .replace("xmlns=\"\" ", "") }
  }


  /****************************************************************************/
  private fun doIt1 ()
  {
    /***************************************************************************/
    fun x () = Logger.announceAllAndTerminateImmediatelyIfErrors() // A little shorthand.



    /***************************************************************************/
    /* Arrange to turn the raw inputs into OSIS.  In previous implementations,
       subsequent processing would check that we had verse eids and that they
       were positioned in a reasonably 'optimal' manner.  I have now dropped
       the relevant function calls here, so I rely upon the things invoked by
       Builder_InitialOsisRepresentationOfInputs.process to sort that out. */

    Builder_InitialOsisRepresentationOfInputs.process(); x()
    Rpt.report(0, banner())
    InternalOsisDataCollection.loadFromDoc(ExternalOsisDoc); x()



    /**************************************************************************/
    /* It is useful at this juncture to remove from the text any footnotes etc.
       Most of the subsequent processing can safely ignore them, and by removing
       them we can avoid the overhead of running over them repeatedly while
       looking for the things we actually _want_ to process.  We reinstate them
       later. */

    val notesArchiver = PA_ElementArchiver()
    notesArchiver.archiveElements(InternalOsisDataCollection, InternalOsisDataCollection.getFileProtocol()::isNoteNode); x()



    /***************************************************************************/
    /* Load data and carry out basic evaluation. */

    Rpt.report(level = 0, "Loading and analysing data.")
    BookOrdering.initialiseFromOsis(ExternalOsisDoc); x()
    Osis_ChapterStructurePreprocessor.process(InternalOsisDataCollection); x() // Turns milestone chapters into enclosing tags, create missing chapters, etc.
    PA_EnhancedVerseEndInserter.process(InternalOsisDataCollection)
    RefBase.setBibleStructure(InternalOsisDataCollection.getBibleStructure()); x()  // Obtain book / chapter / verse details.



    /**************************************************************************/
    /* Miscellaneous changes to correct things or convert them into a standard
       form which is easier to handle. */

    Rpt.report(level = 0, "Handling Strongs, tables and elisions.")
    PA_EnclosingTagToSelfClosingConverter.process(InternalOsisDataCollection); x() // Does what it says on the tin.
    PA_StrongsHandler.process(InternalOsisDataCollection); x()                     // Canonicalise Strong's markup.
    Osis_BasicTweaker.process(InternalOsisDataCollection); x()                     // Minor changes to make processing easier.
    PA_TableHandler.process(InternalOsisDataCollection); x()                       // Collapses tables which span verses into a single elided verse.  $$$ Is this needed?
    PA_ElisionHandler.process(InternalOsisDataCollection); x()                     // Expands elisions out into individual verses.
    PA_ListEncapsulator.process(InternalOsisDataCollection); x()                   // Might encapsulate lists (but in fact does not do so currently).
    //Dbg.d(InternalOsisDataCollection.convertToDoc())
    
    
    /**************************************************************************/
    Rpt.report(level = 0, "Handling canonical headings and lists.")
    PA_CanonicalHeadingsHandler.process(InternalOsisDataCollection); x()  // Canonicalises canonical headings, as it were.


    
    /**************************************************************************/
    /* Basic validation -- things which have to be correct in order for any
       further processing to make sense: for example, are all verses within
       chapters? */

    Rpt.report(level = 0, "Performing basic validation.")
    Osis_BasicValidator.process(InternalOsisDataCollection); x()



    /**************************************************************************/
    /* Reversification. */

    Rpt.report(level = 0, "Performing reversification if necessary.")
    Osis_AudienceAndCopyrightSpecificProcessingHandler.doReversificationIfNecessary(InternalOsisDataCollection); x()
    //Dbg.d(InternalOsisDataCollection.convertToDoc())



    /**************************************************************************/
    /* osis2mod can't cope with holes in the versification structure, so we need
       to make sure there aren't any.  If not reversifying, we also need to
       ditch any books which the selected scheme cannot handle, and check that
       the result is reasonably well aligned with whatever Crosswire
       versification scheme the user may have selected.

       Ideally I'd have done this earlier in the processing chain, but some of
       the reversification data checks to see if verses are missing, and if I
       filled them in myself before getting as far as the reversification
       processing, that would be thwarted. */

    Rpt.report(level = 0, "Checking alignment with scheme if necessary, and filling in any missing verses.")
    Osis_BasicAlignerToScheme.process(InternalOsisDataCollection); x()
    //Dbg.d(InternalOsisDataCollection.convertToDoc())


    /**************************************************************************/
    /* osis2mod can't cope with subverses, so if there are any, we need to
       collapse them into their parent verse.  This must happen only _after_
       reversification, because that may rely upon the subverses still existing.

       Note, incidentally, that we are concerned here with subverses which are
       overtly marked as such with tags in the original.  If the original
       simply had things (a) embedded in the text to flag subverse
       boundaries, there is nothing we need to do. */

    Rpt.report(level = 0, "Collapsing subverses if any.")
    PA_SubverseCollapser.process(InternalOsisDataCollection); x()



    /**************************************************************************/
    Rpt.report(level = 0, "Yet more tidying.")
    PA_MissingVerseHandler(InternalOsisDataCollection.getFileProtocol()).markVersesWhichWereEmptyInTheRawText(InternalOsisDataCollection); x() // What it says on the tin.
    PA_MissingVerseHandler.preventSuppressionOfEmptyVerses(InternalOsisDataCollection); x() // Unless we take steps, empty verses tend to be suppressed when rendered.
    PA_TextAnalyser.process(InternalOsisDataCollection); x()                                // Gathers up information which might be useful to someone administering texts.
    Osis_FinalInternalOsisTidier.process(InternalOsisDataCollection, notesArchiver); x()    // Ad hoc last minute tidying.
    //Dbg.d(InternalOsisDataCollection.convertToDoc())
    PA_CalloutStandardiser.process(InternalOsisDataCollection); x()                         // Forces callouts to be in house style, assuming that's what we want.
    Osis_CrossReferenceCanonicaliser.process(InternalOsisDataCollection) ; x()              // Does what it says on the tin.  For example, checks if any cross-references target verses which don't exist in this text.
    Osis_BasicTweaker.unprocess(InternalOsisDataCollection); x()                            // Undoes any temporary tweaks which were applied to make the overall processing easier.
    Osis_InternalTagReplacer.process(InternalOsisDataCollection); x()                       // Replaces any _X_ tags which I introduced with pukka OSIS ones.
    PA_TemporaryAttributeRemover.process(InternalOsisDataCollection); x()                   // Removes all temporary attributes.
    Osis_BooksWithAdditionsHandler.process(InternalOsisDataCollection); x()                 // Handle eg renaming of AddEsth to EsthGr if that's what we decide to do.
  }
}
