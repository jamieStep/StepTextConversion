/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils


/******************************************************************************/
/**
 * Investigates and restructures USX.
 *
 *
 *
 *
 *
 * ## Overview
 *
 * It has seemed useful to me both a) to have the chance to investigate incoming
 * USX and identify any structures which might thwart the automated processing;
 * and b) to modify the incoming data to some extent, in order to simplify
 * later processing -- both of which functions are performed under control of
 * this present class.
 *
 * The class runs over all input files.  It may take its input from either of
 * two folders, and is informed by the caller which of the two is being used
 * on this run.
 *
 * In addition, it creates some files in the TextFeatures folder (that's just
 * some files overall, not some for each input file), one of them giving details
 * of any 'interesting' features which appear in any of the input files, and one
 * giving information about the text (which books it contains, which chapters,
 * etc).  These are not used by the processing, but may be of use when looking
 * for texts with particular characteristics.
 *
 *
 *
 *
 *
 * ## Restructuring
 *
 * Aside from investigating the incoming USX to provide summary information
 * about it, there are a number of reasons why it might be useful to make
 * modifications to the incoming USX before passing it on for further
 * processing :-
 *
 * - USX comes in a number of different versions, and things are simplified
 *   if the main differences between them can be ironed out.
 *
 * - There are a few common traits to the way USX is misused, and it is
 *   useful to see if we can iron these out.
 *
 * - There are some places where I just think a little reordering will
 *   make things easier.
 *
 * - Some tags are level-related (q1, q2, q3 etc), and where this is the
 *   case, USX permits the level-1 version to be given with or without the
 *   '1'.  I add the number here, for the sake of uniformity.
 *
 * - There are some places where USX falls short of what is really needed
 *   for later processing.  As an example, USX supports list items (the
 *   equivalent of the HTML 'li' element), but lacks any means of enclosing
 *   the list (the equivalent of the HTML 'ul').  OSIS, for one, demands
 *   such bracketing items (or it does in theory; in practice I have to say
 *   it seems to fare rather better without).  It therefore seems to make
 *   sense to undertake the complicated processing needed to add the
 *   brackets sooner rather than later (except that as I explain later, I
 *   don't: this present class is the place to do it, but I don't bother).
 *
 * - Cross-references are subject to a lot of checks and potential
 *   restructuring.  More details of this are given in
 *   [CrossReferenceProcessor].
 *
 * - There are some blocks of contiguous related tags which I suspect it
 *   may be useful to encapsulate into a single container node (book
 *   introductions being an obvious example).
 *
 * - There are some bugs (either in osis2mod or in the rendering) which
 *   mean that the output sometimes looks wrong.  Changes here can avoid
 *   these bugs.
 *
 *
 * Historically, I was also making another tranche of particularly complicated
 * changes.  USX supports the use of milestone markers for chapters and verses,
 * with the clear intention that people would then be able to have formatting
 * and semantic markup run across chapter- (or more particularly) verse-
 * boundaries.
 *
 * This is problematical, both from the point of Crosswire's osis2mod tool which
 * we use to create Sword modules and when it comes to reversification.
 *
 * As regards osis2mod, I am sure there was an email thread which suggested
 * that cross-boundary markup was a problem.  More recently I have been told
 * that this is not the case.  However, the fact remains that with some kinds of
 * cross-boundary markup, osis2mod does at least issue warnings, and since it is
 * not clear what may be the implications of this, I tend to the view that we
 * are better trying to avoid such markup where possible.
 *
 * And as regards reversification, there are some places where we physically
 * move verses from one place to another.  Admittedly in the overall scheme of
 * things these are relatively few in number, but having any at all is
 * awkward, because it becomes difficult to excise text from its original
 * position (although I now believe less difficult than I once thought was the
 * case).
 *
 * In an earlier incarnation of the present module, I was attempting to address
 * these issues by turning both verse- and chapter- tags into enclosing tags.
 * I still do this for chapters, in fact, because it seems to me to bring
 * benefits, and the likelihood of encountering cross-chapter markup is
 * vanishingly small.
 *
 * With verses, however, this is just not practical.  Experience suggests that
 * in any given text there are likely to be some places where the markup is
 * such that verse tags can indeed be turned into enclosing tags with no
 * additional work at all; and others where a certain amount of fiddly tweaking
 * can reduce things to a structure in which, again, the verses can be enclosed.
 * But there usually remain some which are not amenable to this kind of
 * treatment.
 *
 * This leaves me in somewhat of a quandary, because there is no way in which I
 * can guarantee a text which satisfies all of the actual or presumed
 * requirements of osis2mod and reversification.  Possibly the approach I have
 * adopted here succeeds in combining the worst of both worlds.  I employ
 * unpleasantly complicated code in order to achieve a situation where hopefully
 * as many verses as possible are indeed 'enapsulable' (in the sense that the
 * start and end tags do indeed effectively encapsulate a single verse, even
 * though they remain as milestones, and there is no cross-boundary markup);
 * but at the same time recognising that there are likely to be quite a few
 * situations in which this cannot be achieved, and I just have to hope that
 * these won't end up causing problems.
 *
 *
 *
 *
 *
 * ## Outputs
 *
 * The class generates a) a file summarising any 'important' features of the
 * text; b) a file giving details of the structure of the text (which books it
 * contains, the number of the last chapter for each book, etc); and c) revised
 * and restructured USX.
 *
 * The first two of these are placed into the TextFeatures folder, under the
 * names textFeatures.json and vernacularBibleStructure.json respectively (both
 * of them in extended JSON format -- ie with // used as comment markers).
 *
 * The restructured USX is stored in the EnhancedUsx folder, one file for
 * each of the input files.
 *
 * In addition to highlighting any 'interesting' features in the text,
 * textFeatures.json also lists any books in which errors were detected.
 *
 * Similarly, in addition to giving basic details about which books, chapters
 * and verses are present, vernacularBibleStructure.json also lists missing and
 * duplicate verses.  Duplicate verses are definitely a problem.  Missing verses
 * may or may not be -- some texts lack certain verses, but these can be filled
 * in by reversification.
 *
 *
 *
 *
 * ## Code structure
 *
 * The processing here involves a huge amount of code.  Some of it runs
 * immediately.  Some of it handles reversification on these texts where
 * we restructure the text during the conversion process (likely to be few in
 * number -- restructuring is usually ruled out by licence conditions).  And
 * some of it needs to run every time, but must be deferred until after the
 * reversification processing.
 *
 * I have therefore found it convenient to have the present class handle the
 * control aspect, and to offload the actual processing to the classes
 * [TextConverterProcessorXToUsxB_PreReversificationProcessor],
 * [TextConverterProcessorXToUsxB_ReversificationProcessor] and
 * [TextConverterProcessorXToUsxB_PostReversificationProcessor]
 *
 *
 *
 *
 *
 * ## Pre-processing
 *
 * Because USX is not always supplied in the form we would like, I permit some
 * pre-processing to massage it into a more usable form.
 *
 * This can be done in one of three ways.  You can supply an external program
 * which runs over all of the input USX and creates revised files.  This is
 * handled in class [TextConverterProcessorInputUsxToUsxA].
 *
 * You can provide a JAR file which conforms to a particular API spec.  This is
 * loaded into the converter at run-time and run directly from the code in
 * [TextConverterProcessorXToUsxB_PreReversificationProcessor].
 *
 * Or you can supply XSLT fragments in a configuration parameter.  These, again,
 * are applied in [TextConverterProcessorXToUsxB_PreReversificationProcessor].
 *
 *
 *
 *
 *
 * ## Debugging
 *
 * It may also be useful, particularly where restructuring has been applied, to
 * retain rudimentary information about what the changes have been.  This is
 * handled by _X_dbg tags.  Whether these appear or not is controlled by a
 * command line parameter.
 *
 *
 *
 *
 *
 * ## Weasel words
 *
 * I have made no attempt to make this code efficient -- it has been more than
 * enough to attempt to make it clear (and even here I suspect I have failed
 * miserably).
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterProcessorXToUsxB : TextConverterProcessor
{
  /****************************************************************************/
  override fun banner () = "Creating enhanced USX"
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor) {}

  override fun prepare ()
  {
    StepFileUtils.deleteFolder(StandardFileLocations.getInternalUsxBFolderPath())
    StepFileUtils.createFolderStructure(StandardFileLocations.getInternalUsxBFolderPath())
  }


  /****************************************************************************/
  override fun process ()
  {
    /**************************************************************************/
    ReversificationData.process()



    /**************************************************************************/
    /* Pre-reversification.  The let blocks here and below are a half-hearted
       attempt to make sure memory is released -- some of these things use
       rather a lot of it.

       The initial input must be USX files, so we need to be looking either at
       the InputUsx or UsxA folder.  The latter will have been populated if
       the processing started from VL, or if the InputUsx was pre-processed,
       so if it contains anything, that's the folder to use.  Otherwise,
       the input must be in InputUsx. */

    let {
      val inFolder = if (StepFileUtils.fileOrFolderExists(StandardFileLocations.getInternalUsxAFolderPath()) && !StepFileUtils.folderIsEmpty(StandardFileLocations.getInternalUsxAFolderPath())) StandardFileLocations.getInternalUsxAFolderPath() else StandardFileLocations.getInputUsxFolderPath()
      val inFiles = StepFileUtils.getMatchingFilesFromFolder(inFolder, ".*\\.usx".toRegex()).map { it.toString() }
      val processor = TextConverterProcessorXToUsxB_PreReversificationProcessor()
      inFiles.forEach { processor.processFile(it) }// Creates the enhanced USX.
    }



    /**************************************************************************/
    /* Reversification on those few texts to which it is applied. */

    if (TextConverterProcessorXToUsxB_ReversificationProcessor.runMe())
      let {
        Dbg.reportProgress("")
        Dbg.reportProgress("Reversifying")
        Osis2ModInterface.instance().createSupportingData()
        TextConverterProcessorXToUsxB_ReversificationProcessor.process()
      }



    /**************************************************************************/
    /* Post-reversification. */

    let {
      Dbg.reportProgress("")
      Dbg.reportProgress("Creating enhanced USX -- Part 2")
      Osis2ModInterface.instance().createSupportingData()

      val inFolder = StandardFileLocations.getInternalUsxBFolderPath()
      val inFiles = StepFileUtils.getMatchingFilesFromFolder(inFolder, ".*\\.usx".toRegex()).map { it.toString() }
      val processor = TextConverterProcessorXToUsxB_PostReversificationProcessor()
      inFiles.forEach { processor.processFile(it) }// Creates the enhanced USX.
    }
  }
}
