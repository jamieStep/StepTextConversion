/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.*
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.doNothing
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.getExtendedNodeName
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.makeFootnote
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.recordTagChange
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.reportBookBeingProcessed
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.runCommand
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.Translations
import org.stepbible.textconverter.support.miscellaneous.Zip
import org.stepbible.textconverter.support.ref.*
import org.stepbible.textconverter.support.shared.Language
import org.stepbible.textconverter.support.shared.SharedData
import org.stepbible.textconverter.support.usx.Usx
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
 * The class assumes a standard folder structure, and runs over all files in the
 * RawUsx folder (or, where pre-processing has been applied, some combination of
 * the content of the raw and standardised folders). For each, it creates a
 * restructured file with the same name in the StandardisedUsx folder.
 * Where pre-processing has been applied, this new file replaces the one created
 * by pre-processing.
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
 *
 * ## Pre-processing
 *
 * Because of the complexity of the USX standard and of the different
 * requirements and different assumptions brought to things by the various
 * translators, many texts are a law unto themselves to some extent or other.
 * This suggests a need to have text-specific pre-processing to iron out these
 * differences.  Without that, the converter would have to be extended with
 * each new text it was required to process, making it vastly more baroque even
 * than it is already.
 *
 * To address this, I make provision for separate pre-processing to be supplied.
 * The pre-processor must be either a batch file named preprocessor.bat, a
 * Python script named preprocessor.py (and you must have Python available
 * and runnable from the command-line), or a preprocessor.exe, and must sit in a
 * directory called Preprocessor within the root folder for the text.  If more
 * than one of these exists, batch files take precedence over the others, and
 * .exe's over .py's.
 *
 * Rules are as follows :-
 *
 * - The pre-processor will receive two arguments -- the path to the input
 *   folder, and the path to the output folder.
 *
 * - All relevant input files will have an extension of .usx.  Files with
 *   other extensions should be ignored.
 *
 * - The output folder will be empty at the time the pre-processor is
 *   invoked.
 *
 * - Output files must be placed in the output folder, must have an
 *   extension of .usx, and must have the same name as the input file
 *   which gave rise to them.
 *
 * - Output files must be valid 'standard' USX.
 *
 * - It's unlikely that you will want to create a file in the output folder
 *   for which you have no input counterpart, but there is nothing to
 *   prevent you from doing so.  In this case, you can call the new file
 *   anything you like so long as it has the .usx suffix.
 *
 * - If a file with a given name exists in both the output and input folder,
 *   the converter processes the output version.
 *
 * - If a file with a given name exists only in the input folder, the
 *   converter processes it.
 *
 * - The effect of these previous bullet points is that a) you *can*
 *   create entirely new files if you need to; b) you can force a revised
 *   version of a file to be used by giving the revised version the same
 *   name as the original; and c) there is no need to copy files to the
 *   output folder if you are not applying changes to them (although there
 *   is no harm if you do so).
 *
 * - This leaves only the issue of how the pre-processor can *prevent<*
 *   an input file from being processed.  Again this is probably an unlikely
 *   eventuality, but if you should need to do it, simply create an empty
 *   file in the output folder with the same name as the input file.
 *
 *
 *
 *
 *
 *
 * ## Debugging
 *
 * It may also be useful, particularly where restructuring has been applied, to
 * retain rudimentary information about what the changes have been.  This is
 * handled by _X_dbg tags.  Whether these appear or not is controlled by the
 * debugLevel command line parameter.
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

object TextConverterProcessorUsxToEnhancedUsx1 : TextConverterProcessorBase()
{
    /******************************************************************************************************************/
    override fun banner (): String
    {
        return "Creating enhanced USX -- Part 1"
    }


    /******************************************************************************************************************/
    override fun pre (): Boolean
    {
        createFolders(listOf(StandardFileLocations.getEnhancedUsxFolderPath(), StandardFileLocations.getPreprocessedUsxFolderPath(), StandardFileLocations.getTextFeaturesFolderPath()))
        deleteFiles(listOf(Pair(StandardFileLocations.getEnhancedUsxFolderPath(), "*.usx"),
                           Pair(StandardFileLocations.getPreprocessedUsxFolderPath(), "*.usx"),
                           Pair(StandardFileLocations.getTextFeaturesFolderPath(), "*.json")))
        return true
    }


    /******************************************************************************************************************/
    override fun runMe (): Boolean
    {
        return true
    }



    /******************************************************************************************************************/
    /**
     * @{inheritDoc}
     */

    override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
    {
        commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
    }


    /******************************************************************************************************************/
    override fun process (): Boolean
    {
      runPreprocessor()
      BibleStructure.UsxUnderConstructionInstance().populateFromBookAndFileMapper(BibleBookAndFileMapperRawUsx) // Gets the chapter / verse structure -- how many chapters in each verse, etc.
      forceVersificationSchemeIfAppropriate()
      ReversificationData.process()                                              // Does what it says on the tin.  This gives the chance (which I may not take) to do 'difficult' restructuring only where reversification will require it.
      BibleBookAndFileMapperRawUsx.iterateOverSelectedFiles(::processFile)       // Creates the enhanced USX.
      return true
    }


    /******************************************************************************************************************/
    /* With some texts, we have to do a little pre-processing to massage them into a reasonable shape.  The constraints
       upon the pre-processor are ...

       * The code must be in the Preprocessor folder under the root for the text.

       * It must be called preprocessor.xxx, where xxx is bat, exe, jar, js or py.  I suggest you avoid having more
         than one of these in a given folder.  If by any mischance you do, .bat takes priority, but after that you
         should assume that the choice is essentially random.

       * If you use .jar, you must have the java run-time available on your computer and accessible via PATH (although
         of course you will do, or else you couldn't be running the present code).

       * If you use .js, you must have node.js available on your computer and accessible via PATH.

       * If you use .py, you must have Python available on your computer and accessible via PATH.

       * It will be called with the arguments (in the following order): PreprocessedUsxFolder, RawUsxFolder, [fileList]
         where [fileList] is optional.  If present it is a fullstop-separated list of abbreviated book names.  It is
         passed only on runs which are using the Dbg facilities to limit the list of files being processed for debug
         purposes, and it lists those files.  You don't _have_ to take it into account, but if you're debugging the
         present code and the preprocessor fails to take it into account, things could get confusing, because you're
         likely to end up with files lying around which you aren't expecting.

       * The preprocessor must take files from the raw folder and create them under the same name in the preprocessed
         folder, applying whatever changes are necessary in the process.  If no changes are applied to a particular
         file, there is no need to create a copy of the file in the preprocessed folder -- but not problem if you do.
    */

    private fun runPreprocessor ()
    {
      /****************************************************************************************************************/
      val command: MutableList<String> = ArrayList()

      if (Files.exists(Paths.get(StandardFileLocations.getPreprocessorExeFilePath())))
        command.add(Paths.get(StandardFileLocations.getPreprocessorExeFilePath()).toString())
      else if (Files.exists(Paths.get(StandardFileLocations.getPreprocessorBatchFilePath())))
        command.add(Paths.get(StandardFileLocations.getPreprocessorBatchFilePath()).toString())
      else if (Files.exists(Paths.get(StandardFileLocations.getPreprocessorJavaFilePath())))
      {
        command.add("java")
        command.add("-jar")
        val mainClass = Zip.getInputStream(StandardFileLocations.getPreprocessorJavaFilePath(), "META-INF/MANIFEST.MF")!!.first.bufferedReader().readLines().first { it.contains("Main-Class") }.split(" ")[1]
        command.add(mainClass) // Not sure we actually need this.
        command.add(Paths.get(StandardFileLocations.getPreprocessorBatchFilePath()).toString())
      }
      else if (Files.exists(Paths.get(StandardFileLocations.getPreprocessorJavascriptFilePath())))
      { command.add("node"); command.add(Paths.get(StandardFileLocations.getPreprocessorBatchFilePath()).toString()) }
      else if (Files.exists(Paths.get(StandardFileLocations.getPreprocessorPythonFilePath())))
      { command.add("python"); command.add(Paths.get(StandardFileLocations.getPreprocessorPythonFilePath()).toString()) }



      /***************************************************************************************************************/
      if (command.isEmpty()) return



      /***************************************************************************************************************/
      val booksToBeProcessed = Dbg.getBooksToBeProcessed().joinToString(".")
      command.add(StandardFileLocations.getPreprocessedUsxFolderPath())
      command.add(StandardFileLocations.getRawUsxFolderPath())
      if (booksToBeProcessed.isNotEmpty()) command.add(booksToBeProcessed)
      runCommand("  Preprocessing: ", command)
      Dbg.reportProgress("Preprocessing complete", 1)
    }


    /******************************************************************************************************************/
    /* An eclectic set of changes.  Anything labelled a) does not rely upon the revised chapter / verse structure which
       I generate.  Things labelled b) produce that structure.  Things labelled c) rely upon it, or need to run after
       other things which do rely upon it.

       !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

       IMPORTANT: Note that some of the restructuring carried out here may have a significant impact upon the formatting
       of the rendered text.  In fact, my attempts at 'sensible' positioning of verse ids will not do so, but tables are
       a rather different matter.  I restructure tables partly because if the verses they contain are affected by
       reversification, the reversification processing will find it almost impossible to handle them -- at least given
       the markup applied to the majority of tables I've seen to date.  And I do it also because osis2mod sometimes
       complains about cross-verse-boundary markup, and tables are a likely source of such markup.

       As regards osis2mod, these considerations are speculative -- I have not found any pattern as to what kinds of
       things cause osis2mod to issue warnings and which do not -- nor to whether it actually gets things wrong when
       it issues warnings, or gets them right when it does not.

       As regards reversification, I believe it probably _is_ useful to carry out the kind of restructuring I do here;
       but of course only to tables which are affected by reversification.

       If you wanted a more nuanced approach, you can call ReversificationData.initialiseIfNecessary at the end of
       block A below, and then use the information it can supply about cases where verses are to be moved.  This would
       enable you to identify 'at risk' tables, and reformat just those.  (There is no problem doing this even on
       non-reversification runs: ReversificationData.initialiseIfNecessary will do nothing, and the information
       regarding moves will be empty.)

       Bear in mind, though, that of the various attempts at restructuring below, at least one actually produces
       rendered text which looks rather better than would be the case by default.
    */

    private fun processFile (bookName: String, rawUsxPath: String, document: Document)
    {
        /**********************************************************************/
        fun x () { if (Logger.getNumberOfErrors() > 0) panic() }



        /**********************************************************************/
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //!!!!!!!!!!!!!!!! DO READ THE HEAD-OF-METHOD COMMENTS !!!!!!!!!!!!!!!!!
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

        initialise(rawUsxPath, document)                   // a) Get book details etc.
        validateInitialAssumptions(); x()                  // a) Check that things I rely upon later (or think I _may_ rely upon) do actually hold.
        deleteIgnorableTags()                              // a) Anything of no interest to our processing.
        correctCommonUsxIssues()                           // a) Correct common errors.
        simplePreprocessTagModifications()                 // a) Sort out things we don't think we like.
        canonicaliseRootAndBook()                          // a) Change usx tag to _X_usx, book to _X_book, and turn the latter into an enclosing tag.
        canonicaliseChapterAndVerseStarts(); x()           // a) Make sure we have sids (rather than numbers); that there are no verse-ends (we deal with those ourselves) and that chapters are enclosing tags.
        handleStrongs()                                    // a) Iron out any idiosyncrasies in Strong's representations.
        forceCallouts()                                    // a) Override any translator-supplied callouts and use our own standard form.
        convertTagsToLevelOneWhereAppropriate()            // a) Some tags can have optional level numbers on their style attributes.  A missing level corresponds to leve 1, and it's convenient to force it to be overtly marked as level 1.
        encapsulateLists()                                 // a) Sort out list structures.
        encapsulateHeadings()                              // a) For later processing it may be useful to encapsulate headers; or maybe it's just my aesthetic sense.
        CrossReferenceProcessor.canonicalise(document)     // a) There are all sorts of awkward things about refs and associated tags which it would be nice to sort out.

        positionVerseEnds(); x()                           // b) Move sids and eids where possible to avoid cross-verse-boundary markup.

        getSampleTextForConfigData()                       // c) Supplies ConfigData with some sample text which it can use to determine text direction.
        getFeatureDetailsCountVersesInParas()              // c) Does what it says on the tin.  I just don't know _why_ I need to do it ...
        bracketIntroductions()                             // c) Wrap a container around book intro material.
        expandElisions()                                   // c) Replaces elisions by individual verses.
        markCanonicalTitleLocations()                      // c) Some psalms have canonical titles at the end as well as the beginning.  It's useful to mark the para:d's to say where they are.
        deleteTrailingBlankLinesInChapters()               // c) The rendering gets messed up if chapters have trailing blank lines.


        val dt = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMMM-yyyy"))
        Dom.outputDomAsXml(m_Document, Paths.get(StandardFileLocations.getEnhancedUsxFolderPath(), StepFileUtils.getFileName(rawUsxPath)).toString(), "STEP extended USX created $dt")
    }





    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /**                                                                                                              **/
    /**                                       Check for things of interest                                           **/
    /**                                                                                                              **/
    /******************************************************************************************************************/
    /******************************************************************************************************************/

     /******************************************************************************************************************/
    /* For some reason I haven't fathomed, the Sword config file needs to have a setting which relates to para usage.
       I _think_ what they require is to know whether there are any paras containing more than one verse, although
       I have to say I'm not too clear about that -- what they actually seem to be concerned with is whether we have
       verse-per-line or not. */

    private fun getFeatureDetailsCountVersesInParas ()
    {
        /**************************************************************************************************************/
        /* If we already know we have multiverse paras, there's no point in checking again. */

        if (SharedData.SpecialFeatures.hasMultiVerseParagraphs())
            return



        /**************************************************************************************************************/
        fun countVerses (x: Node)
        {
            val style = Dom.getAttribute(x, "style")
            if ("p" != style && "q" != style && "l" != style) return

            val descendants = Dom.collectNodesInTree(x)
            val n = descendants.count { "verse" == Dom.getNodeName(it) }
            if (n > 1)
            {
                SharedData.SpecialFeatures.setHasMultiVerseParagraphs()
                throw StepException("") // No need for further processing -- we now know all that's needed.
            }
        }


        /**************************************************************************************************************/
        try
        {
            Dom.findNodesByName(m_Document, "para").forEach { countVerses(it) }
        } catch (_: StepException)
        {
            // Here simply so countVerses can exit prematurely.
        }
    }


    /******************************************************************************************************************/
    /* Sets the sample text needed by ConfigData to determine text direction. */

    private fun getSampleTextForConfigData ()
    {
      if (!ConfigData.wantsSampleText()) return
      val x = Dom.findAllTextNodes(m_Document)
               .filter {! Usx.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(it) }
               .first { it.textContent.length > 5 }.textContent
        ConfigData.setSampleText(x)
    }





    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /**                                                                                                              **/
    /**                                             'Simple' changes                                                 **/
    /**                                                                                                              **/
    /******************************************************************************************************************/
    /******************************************************************************************************************/

    /******************************************************************************************************************/
    /* Brackets introductory material at the start of a book. */

    private fun bracketIntroductions ()
    {
      /****************************************************************************************************************/
      bracketIntroduction(Dom.findNodeByName(m_Document,"_X_book")!!)
      Dom.findNodesByName(m_Document, "_X_chapter").forEach { bracketIntroduction(it) }
    }


    /******************************************************************************************************************/
    private fun bracketIntroduction (owner: Node)
    {
      /****************************************************************************************************************/
      val (style, stopAt) = if ("_X_book" == Dom.getNodeName(owner)) Pair("book", "_X_chapter") else Pair("chapter", "verse")
      var blocks = 0



      /****************************************************************************************************************/
      val nodes = Dom.collectNodesInTree(owner)
      val stopAtNode = Dom.findNodeByName(owner, stopAt, false)



      /****************************************************************************************************************/
      fun encapsulate (ixLow: Int, ixHigh: Int)
      {
        ++blocks
        val container = Dom.createNode(m_Document, "<_X_introductionBlock style='$style'/>")
        val topLevel = Dom.pruneToTopLevelOnly(nodes.subList(ixLow, ixHigh))
        Dom.insertNodeBefore(topLevel[0], container)
        Dom.deleteNodes(topLevel)
        Dom.addChildren(container, topLevel)
      }



      /****************************************************************************************************************/
      var ixLow = -1
      var ixHigh: Int

      while (true)
      {
        while (nodes[++ixLow] !== stopAtNode)
          if (Usx.isIntroductionNode(nodes[ixLow])) break

        if (nodes[ixLow] === stopAtNode)
          break

        ixHigh = ixLow
        while (nodes[++ixHigh] !== stopAtNode)
           if (!Usx.isIntroductionNode(nodes[ixHigh]) &&
               !Dom.isWhitespace((nodes[ixHigh])) &&
               null == Dom.getAncestorSatisfying(nodes[ixHigh]) { n -> Usx.isIntroductionNode(n) }) break

        encapsulate(ixLow, ixHigh)

        if (nodes[ixHigh] === stopAtNode)
          break

        ixLow = ixHigh
      }
    }


    /******************************************************************************************************************/
    /* Renames the root note as _X_usx, and the book node as _X_book, and turns the latter into an enclosing node. */

    private fun canonicaliseRootAndBook ()
    {
      val rootNode = Dom.findNodeByName(m_Document, "usx")!!
      Dom.setNodeName(rootNode, "_X_usx")

      val topLevelChildren = Dom.getChildren(rootNode)
      val ix = topLevelChildren.indexOfFirst { "book" == Dom.getNodeName(it) }
      val bookNode = topLevelChildren[ix]
      Dom.deleteChildren(bookNode)

      topLevelChildren.subList(ix + 1, topLevelChildren.size).forEach { Dom.deleteNode(it); bookNode.appendChild(it) }
      Dom.setNodeName(bookNode, "_X_book")
      Dom.deleteAttribute(bookNode, "style")
    }


    /******************************************************************************************************************/
    /* Different versions of USX have different ways of marking chapters and verses.  USX 2 required markers at the
       start only, with the chapter or verse number identified with a 'number' attribute. USX 3 requires a separate
       milestone marker at both start and end, with a sid on the former and an eid on the latter, both of which are full
       references.  I therefore definitely want to accommodate both of these; but I feel that it's appropriate too to
       try to cater for combinations which more or less fit this picture, but aren't _quite_ right.

       I make the assumption below that the data will at least be consistent (so that if, for instance, we have _any_
       chapter/sids, we'll have chapter/sids throughout). */

    private fun canonicaliseChapterAndVerseStarts ()
    {
        /**************************************************************************************************************/
        /* Assume if we have any chapter:eids, we're going to have chapter:eids throughout; and that if we have any
           verse:eids, we'll have verse:eids throughout.  And because I want to position these things for myself, I
           need to delete all of them.  Except that if the original source was VL, I can be confident that eids are
           already in the right place, so in this case I retain the verse eids. */

        Dom.findNodesByAttributeName(m_Document, "chapter", "eid").forEach { Dom.deleteNode(it) }
        if ("VL" != m_SourceConversion) Dom.findNodesByAttributeName(m_Document, "verse",   "eid").forEach { Dom.deleteNode(it) }



        /**************************************************************************************************************/
        /* In some texts, chapter and verse nodes carry style parameters.  I'm not sure what it's supposed to achieve,
           but it can get in the way of processing. */

        val chapters = Dom.findNodesByName(m_Document, "chapter")
        val verses   = Dom.findNodesByName(m_Document, "verse")
        chapters.forEach { Dom.deleteAttribute(it, "style")}
        verses  .forEach { Dom.deleteAttribute(it, "style")}



       /**************************************************************************************************************/
       /* Temporarily add a dummy chapter sid at the end of the book, then turn each chapter into an enclosing
          chapter, and then get rid of the dummy chapter. */

       val bookNode = Dom.findNodeByName(m_Document, "_X_book")!!
       val dummyChapter = Dom.createNode(m_Document, "<chapter _TEMP_dummy='y'/>")
       bookNode.appendChild(dummyChapter)
       val childrenOfBookNode = Dom.getChildren(bookNode)

       var low = 0
       while (true)
       {
         if ("chapter" != Dom.getNodeName(childrenOfBookNode[low])) // Move forward to the next chapter node (which will mark the start of the chapter being worked on).
         {
           ++low
           continue
         }

         val lowChapterNode = childrenOfBookNode[low]
         if (Dom.hasAttribute(lowChapterNode, "_TEMP_dummy")) break

         var high = low
         while ("chapter" != Dom.getNodeName(childrenOfBookNode[++high])) // Move forward to the next chapter (which will be off the end of the previous chapter).
           doNothing()

         childrenOfBookNode.subList(low + 1, high).forEach { Dom.deleteNode(it); lowChapterNode.appendChild(it) } // Take the intervening nodes as children of the starting chapter node.

         Dom.setNodeName(lowChapterNode, "_X_chapter")

         low = high
       } // while.

       Dom.deleteNode(dummyChapter)



       /**************************************************************************************************************/
       /* Having got this far, we have no eids.  I definitely do want sids, however, and possibly we have 'number'
          attributes instead of sids.  If so, we need to replace the 'number' parameters by sids. */

       if (!Dom.hasAttribute(chapters[0], "sid")) // Assume if the first chapter does not have a sid, neither chapters nor verses will have them.
       {
         var chapterSid = ""

         fun addVerseSid (verse: Node)
         {
           Dom.setAttribute(verse, "sid", "$chapterSid:${Dom.getAttribute(verse, "number")}")
           Dom.deleteAttribute(verse, "number")
         }

         fun addChapterSid (chapter: Node)
         {
           val number = Dom.getAttribute(chapter, "number")
           chapterSid = "$m_BookName $number"
           Dom.setAttribute(chapter, "sid", chapterSid)
           Dom.deleteAttribute(chapter, "number")
           Dom.findNodesByName(chapter, "verse", false).forEach { addVerseSid(it) }
         }

         chapters.forEach { addChapterSid(it) }
       }



       /**************************************************************************************************************/
       /* We get a useful degree of uniformity if we have a dummy verse with a high verse number at the end of each
          chapter (sid only). */

       fun addDummyVerse (chapter: Node)
       {
         val id = Dom.getAttribute(chapter, "sid") + ":" + RefBase.C_BackstopVerseNumber
         val newSid = Dom.createNode(m_Document,"<verse sid='$id' _TEMP_dummy='y'/>")
         val newEid = Dom.createNode(m_Document,"<verse eid='$id' _TEMP_dummy='y'/>")
         val newContent = Dom.createNode(m_Document, "<_TEMP_dummy/>") // I want the verse to have some content so it doesn't get treated as an empty verse.
         chapter.appendChild(newSid)
         chapter.appendChild(newContent)
         chapter.appendChild(newEid)
       }

       chapters.forEach { addDummyVerse(it) }



       /**************************************************************************************************************/
       chapters.forEach { Dom.deleteAttribute(it, "number") }
       Dom.findNodesByName(m_Document, "verse").forEach { Dom.deleteAttribute(it, "number") }
    }


    /******************************************************************************************************************/
    /* Convert eg style='q' to style='q1'. */

    private fun convertTagsToLevelOneWhereAppropriate()
    {
        val nodes = Dom.findNodesByAttributeName(m_Document, "*", "style")
        fun convertTag (tagNamePlusStyle: String)
        {
          val styleName = tagNamePlusStyle.split(":")[1]
          nodes.filter { styleName == Dom.getAttribute(it, "style") }
               .forEach { Dom.setAttribute(it, "style", styleName + 1) }
        }

        Usx.getTagsWithNumberedLevels().forEach { convertTag(it) }
    }


    /******************************************************************************************************************/
    /* There are common ways in which USX is misused.  I suspect in fact that some of these arise because people work
       with texts in USFM, and possibly USX and USFM are not fully aligned, or Paratext doesn't do an entirely
       seamless job in converting from USFM to USX.  Whatever the reason, we need to straighten things out as best
       we can. */

    private fun correctCommonUsxIssues ()
    {
      /************************************************************************/
      val C_ParaTranslations = mapOf("para:po" to "para:pmo",           // Epistle introductions.
                                     "para:lh" to "_X_contentOnly",     // List headers (I think these are new-ish, and are equivalent to HTML <ul>.
                                     "para:lf" to "_X_contentOnly")     // Ditto (footers).  In the one text I have seen which has lh and lf tags, they are used inconsistently, so I am simply dropping them.


      val C_CharTranslations= mapOf("char:cat" to "_X_suppressContent", // Used by Biblica to hold administrative information.
                                    "char:litl" to "_X_listTotal")      // Total field for list items (eg Manasseh _12_).



     /************************************************************************/
     fun changeNode (translations: Map<String, String>, node: Node)
     {
       val translation = translations[getExtendedNodeName(node)]?.split(":") ?: return
       recordTagChange(node, translation[0], if (2 == translation.size) translation[1] else null, "Corrected raw USX markup")
     }


     /************************************************************************/
     Dom.findNodesByName(m_Document, "para").forEach { changeNode(C_ParaTranslations, it) }
     Dom.findNodesByName(m_Document, "char").forEach { changeNode(C_CharTranslations, it) }
    }


    /******************************************************************************************************************/
    /* There are certain kinds of tags which are meaningless in an electronic version of a text, or which we can't
       handle, and these I delete. */

    private fun deleteIgnorableTags ()
    {
        var ignorableTags = Dom.findNodesByAttributeValue(m_Document, "para", "style", "toc\\d")
        ignorableTags.forEach { Dom.deleteNode(it) }

        ignorableTags = Dom.findNodesByName(m_Document, "figure")
        ignorableTags.forEach { Dom.deleteNode(it) }
    }


    /******************************************************************************************************************/
    /* Something somewhere -- osis2mod perhaps? -- screws things up if we have para:b immediately before chapter ends:
       the verse number for the final verse of the chapter is placed _after_ the verse.  Given that there is no point in
       having para:b at the end of a chapter anyway, I delete them here. */

    private fun deleteTrailingBlankLinesInChapters ()
    {
        fun deleteTrailingBlanks (chapter: Node)
        {
          if (!chapter.hasChildNodes()) return

          var  lastChild: Node = chapter.lastChild
          while (Dom.isWhitespace(lastChild))
          {
            Dom.deleteNode(lastChild)
            if (!chapter.hasChildNodes()) break
            lastChild = chapter.lastChild
          }

          while (true)
          {
            if (!chapter.hasChildNodes()) return
            if ("para" != lastChild.nodeName) return
            if (!Dom.hasAttribute(lastChild, "style")) return
            if ("b" != Dom.getAttribute(lastChild, "style")) return
            Dom.deleteNode(lastChild)
          }
        }

        Dom.findNodesByName(m_Document, "_X_chapter").stream().forEach { deleteTrailingBlanks(it) }
    }


    /******************************************************************************************************************/
    /* To simplify later processing, it is convenient to collect headings together into encapsulating nodes.  The aim
       here is to enclose headers into <_X_headingBlock style='...'/>" style is preVerse or inVerse.  At this stage,
       all I want is the encapsulation: I'll worry about the attributes later. */

    private fun encapsulateHeadings ()
    {
      val accumulator: MutableList<Node> = ArrayList()
      val allNodes = Dom.collectNodesInTree(m_Document)
      var ix = -1
      var container: Node? = null

      while (++ix < allNodes.size)
      {
        val node = allNodes[ix]

        if (Usx.isHeadingTag(node))
        {
          if (null == container)
          {
            container = Dom.createNode(m_Document,"<_X_headingBlock/>")
            accumulator.add(node)
          }

        }
        else if (Dom.isWhitespace((node)))
        {
          if (null != container)
            accumulator.add(node)
        }
        else if (null != container)
        {
          val topLevelNodes = Dom.pruneToTopLevelOnly(accumulator)
          Dom.insertNodeBefore(topLevelNodes[0], container)
          Dom.deleteNodes(topLevelNodes)
          Dom.addChildren(container, topLevelNodes)
          container = null
          accumulator.clear()
        }
      } // while
    }


    /******************************************************************************************************************/
    /* Force callouts into standard form.  I'm not sure of the desirability of this, but that's a consideration for
       another day. */

    private fun forceCallouts()
    {
        fun convert(x: Node)
        {
            val style = Dom.getAttribute(x, "style")
            val callout = ConfigData[if ("f" == style) "stepExplanationCallout" else "stepCrossReferenceCallout"]!!
            Dom.setAttribute(x, "caller", callout)
        }

        Dom.findNodesByName(m_Document, "note").forEach { convert(it) }
    }


    /******************************************************************************************************************/
    /* Expands numbers to four characters with leading zeroes; removes spaces; ensures that each element starts with
       'G' or 'H' (I've seen at least one text in which there was a Strong's ref comprising several elements, and
       only the first started this way, the assumption presumably being that the others should assume this same
       value from context). */

    private fun handleStrongs()
    {
        /**************************************************************************************************************/
        fun doStrong(x: Node)
        {
            Dom.setNodeName(x, "_X_strong")
            Dom.deleteAttribute(x, "style")
            val strongsElts = Dom.getAttribute(x, "strong")!!.split("\\W+".toRegex()).toTypedArray()
            for (i in strongsElts.indices) {
                var strong = strongsElts[i].trim().uppercase()
                if (5 != strong.length) {
                    var part = strong.substring(0, 1)
                    if ("G" == part || "H" == part)
                        strong = strong.substring(1)
                    else
                        part = if (i > 0) strongsElts[i - 1].substring(
                            0,
                            1
                        ) else throw StepException("No Strong's prefix: " + Dom.toString(x))

                    if (strong.length < 4) strong = "0000".substring(0, 4 - strong.length) + strong
                    strongsElts[i] = part + strong
                }

                Dom.setAttribute(x, "strong", strongsElts.joinToString(","))
            }
        }


        /**************************************************************************************************************/
        val strongs = Dom.findNodesByName(m_Document, "char").filter { Dom.hasAttribute(it, "strong") }
        strongs.forEach { doStrong(it) }
        if (strongs.isNotEmpty()) SharedData.SpecialFeatures.setStrongs()
    }


    /******************************************************************************************************************/
    /* Some psalms have canonical titles at both beginning and end.  It's useful to mark the para:d's so we can see
       which are which. */

    private fun markCanonicalTitleLocations ()
    {
       var location = ""
       fun process (node: Node)
       {
         when (getExtendedNodeName(node))
         {
           "_X_chapter" -> location = "start"
           "_X_verse"   -> location = "end"
           "para:d"     -> Dom.setAttribute(node, "_X_location", location)
         }
       }

      Dom.collectNodesInTree(m_Document).forEach { process(it) }
    }


    /******************************************************************************************************************/
    /* Changes tagName or tagName+style for another tagName or tagName+style. */

    private fun simplePreprocessTagModifications ()
    {
      val modifications = ConfigData["stepSimplePreprocessTagModifications"] ?: return
      if (modifications.isEmpty()) return
      val individualMods = modifications.split("|").map { it.trim() }
      individualMods.forEach { details ->
        val (from, to) = details.split("->").map { it.trim() }
        val (fromTag, fromStyle) = ( if (":" in from) from else "$from: " ).split(":")
        val (toTag,   toStyle)   = ( if (":" in to  ) to   else "$to: " ).split(":")

        val froms =
          if (":" in from)
            Dom.findNodesByAttributeValue(m_Document, fromTag, "style", fromStyle)
          else
            Dom.findNodesByName(m_Document, fromTag)

        val setStyle = ":" in to

        froms.forEach {
          if (setStyle)
            Dom.setAttribute(it, "style", toStyle)
          else
            Dom.deleteAttribute(it, "style")

          Dom.setNodeName(it, toTag)

          Dom.setAttribute(it, "_X_simplePreprocessTagModification", "$from becomes $to")
        }
      }
    }





    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /**                                                                                                              **/
    /**                                                Elisions                                                     **/
    /**                                                                                                              **/
    /******************************************************************************************************************/
    /******************************************************************************************************************/

    /******************************************************************************************************************/
    /* Expands elisions out into a number of consecutive empty verses, followed by a single verse which contains the
       entire content of the elision.

     * All of the verses are given an _X_originalIid attribute, indicating the original sid of the entire elision.

     * They also get _X_elided to indicate they are in an elision.

     * If the elision was created by the processing here pursuant to table handling, the verses also get _X_wasInTable.

     * The sid tags of the empty verses each get _X_generatedReason='In expanded elision'.

     * The 'master' verse gets _X_masterForElisionWithVerseCountOf with a value giving the number of verses in the
       elision.
    */

    /******************************************************************************************************************/
    private fun expandElisions ()
    {
      fun processChapter (chapterNode: Node)
      {
        val verses = Dom.findNodesByName(chapterNode, "verse", false)
        for (ix in 0..< verses.size - 2 step 2)
        { // -2 to avoid the dummy verses at the end of the chapter.
          if (Dom.getAttribute(verses[ix], "sid")!!.contains("-"))
            expandElision(verses[ix], verses[ix + 1])
        }
      }

      Dom.findNodesByName(m_Document, "_X_chapter").forEach { processChapter(it) }
   }


    /******************************************************************************************************************/
    private fun expandElision (sid: Node, eid: Node)
    {
      /****************************************************************************************************************/
      if (!XXXOsis2ModInterface.C_ExpandElisions) return // $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$ This has downstream ramifications -- it looks as though quite a number of things may need changing, but I need to hear from Sami what he requires.



      /****************************************************************************************************************/
      //Dbg.d("+++++++++++")
      //Dbg.d(sid)
      //Dbg.d(nextSid)
      //Dbg.d("+++++++++++")



      /****************************************************************************************************************/
      val sidRangeAsString = Dom.getAttribute(sid, "sid")!!
      val sidRange = RefRange.rdUsx(sidRangeAsString)



      /****************************************************************************************************************/
      /* Record the location of the elision for possible human consumption. */

      val refKeys = sidRange.getAllAsRefKeys()
      val n = refKeys.size
      val refForVerseContainingTextAsString = sidRange.getHighAsRef().toString()
      Dom.setAttribute(sid, "_X_originalId", sidRangeAsString)
      Dom.setAttribute(sid, "_X_elided", "y")
      Dom.setAttribute(sid, "sid", refForVerseContainingTextAsString)
      Dom.setAttribute(sid, "_X_masterForElisionWithVerseCountOf", "" + n)
      Dom.setAttribute(eid, "eid", refForVerseContainingTextAsString)



      /****************************************************************************************************************/
      /* Generate empty verse markers for the additional verses, all of which are inserted _before_ the actual verse
         which we're carrying over. */

      for (i in 0..< n - 1)
      {
         val refAsString = Ref.rd(refKeys[i]).toString()
         val nodePair = EmptyVerseHandler.createEmptyVerseForElisionAndInsert(sid, refAsString) ?: continue
         val (start, end) = nodePair
         if (Dom.hasAttribute(sid, "_X_wasInTable"))
         {
            Dom.setAttribute(start, "_X_wasInTable", "y")
            Dom.setAttribute(end, "_X_wasInTable", "y")
         }
      }
    }





    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /**                                                                                                              **/
    /**                                          Complex restructuring                                               **/
    /**                                                                                                              **/
    /******************************************************************************************************************/
    /******************************************************************************************************************/

    /******************************************************************************************************************/
    /* This section is concerned with moving verse sids and / or eids around where it is at least moderately easy to
       do so without compromising the intentions of the translators too badly.  The aim is to avoid markup which runs
       across verse boundaries, because this can make reversification difficult and also causes problems with osis2mod
       on occasion.

       More specifically, we can do quite a lot with verse ends, because our sole aim is to place them so that
       non-canonical text does not fall within a verse, but canonical text always does.  This obviously means that
       eid n can't come after sid n+1; but apart from that, I can move eid n backwards towards sid n so long as we
       don't end up with any canonical text outside the verse.

       Shifting sid n is more iffy.  In an earlier version of this code, I was prepared to consider it -- for example
       if it was right at the front of a para.  But the trouble with this is that it _does_ change the way the
       rendered text looks.  If you place the verse marker outside of a para, you get the verse marker on its own and
       the para text on the next line, which doesn't look great.

       There is also a major problem over tables, but that constitutes its own separate section shortly.
     */

    /******************************************************************************************************************/
    private fun positionVerseEnds ()
    {
      if ("VL" == m_SourceConversion) return // No need to do anything if the original input format was VL.

      changeParaPToMilestone()  // Possibly change para:p to milestone, to make cross-boundary markup less of an issue.
      splitEnclosingCharNodes() // If a sid happens to be directly within a char node, split the char node so that the verse can be moved out of it.
      insertVerseEnds()         // Initial positioning.
      expandElisions()          // Don't _really_ want to do this here, because I'll have to do it again later, but I've seen tales which contain elided verses.
      restructureTablesMain()   // Sort out tables.
    }


    /******************************************************************************************************************/
    /* This is another slightly contentious change.  para:p is going to occur extremely commonly in most texts, and
       being an enclosing tag, its sheer ubiquity means that it is quite likely to give rise to cross-book markup.
       However, experiments suggest that the rendered text looks exactly the same if you convert it to a milestone
       tag instead, and in the interests of simplifying things elsewhere, this is what I am doing here.

       The reason I consider it contentious is that the fact that it works may well be down to the particular
       representation of para:p in our stylesheets: there is no guarantee this would work for anyone else, and so to
       make this change could limit our opportunity to make things available to other people.  However, we're already
       engaging in other things like this, so in for a penny in for a pound ... */

    private fun changeParaPToMilestone ()
    {
      // Dom.findNodesByAttributeValue(m_Document, "para", "style", "p").forEach { Dom.convertToSelfClosingNode(it) }
    }


     /******************************************************************************************************************/
     /* We need the 'verses.size - 2' below to avoid processing the final dummy verse. */

     private fun insertVerseEnds ()
     {
       // Dbg.outputDom(m_Document, "a")
       Dom.findNodesByName(m_Document,"_X_chapter").forEach {
         val verses = Dom.findNodesByName(it,"verse", false)
         for (i in 0..< verses.size - 2)
         {
           insertVerseEnd(Dom.getAttribute(verses[i], "sid")!!, verses[i], verses[i + 1])
         }
       }
     }


    /******************************************************************************************************************/
    /* We have a fair degree of liberty when placing end nodes, so long as all canonical text remains within verses.

       We are placing the eid for sid sidWhoseEidWeAreCreating.  nextVerseSid is the next sid after that one.  We
       know that the new eid must be somewhere _before_ the latter.

       But we can probably do better than place it _immediately_ before nextVerseSid, because we can work leftwards
       through the siblings of nextVerseSid, and we need stop only if we find one which is itself canonical or
       contains canonical material.  If we do find one of these 'stop' nodes, then the verse-end is inserted after it
       and that's the end of the processing.

       If we end up being able to move past _all_ of the left-hand siblings of nextVerseSid, we can move up to the
       parent of nextVerseSid, and work through _it's_ left-hand siblings in the same way ... and so on.

       If we haven't already inserted the verse end, eventually, given that chapters are enclosing nodes, we must end up
       at the same level as sidWhoseEidWeAreCreating, or else at the same level as some ancestor of
       sidWhoseEidWeAreCreating.  At this point, we can still carry on moving left, though, because we are bound to hit
       one of three cases: a) a canonical text node; b) a node which _contains_ canonical text; or c) an ancestor of
       sidWhoseEidWeAreCreating.

       Cases a) and b) we have already mentioned above in essence -- they represent stop nodes, and the new eid needs to
       be inserted immediately to their right.

       We're only going to hit case c) (I think) if this ancestor of sidWhoseEidWeAreCreating contains canonical
       text as well as sidWhoseEidWeAreCreating itself.

       In this case, we can now work our way down the tree, tentatively placing the eid as the last child of
       each node, and then moving leftwards over any non-canonical nodes.  If at this point, the eid will be
       a sibling of sidWhoseEidWeAreCreating, we can place the eid at this point.  Otherwise we can work down
       into this node and repeat the process -- the main refinement being the extent to which we can optimise
       this process.

       This then leaves only the case where we have been trying to worm our way downwards, but have concluded
       that this isn't going to let us place the eid as a sibling of sidWhoseEidWeAreCreating.  There seem to
       be two obvious options here -- place the eid at the _lowest_ place in the hierarchy which we have found
       for it, or place it at the _highest_ (ie adjacent to the highest level ancestor of sidWhoseEidWeAreCreating
       which we found in previous processing.
    */

    private fun insertVerseEnd (id: String, sidWhoseEidWeAreCreating: Node, nextVerseSid: Node)
    {
      /****************************************************************************************************************/
      //Dbg.d("=============================")
      //Dbg.dCont(id, "1KI 7:1-12")
      //Dbg.d(sidWhoseEidWeAreCreating)
      //Dbg.d(nextVerseSid)
      //val dbg = Dbg.dCont(id, "HAB 3:1")



      /****************************************************************************************************************/
      fun flagFailure (a: Node, b:Node)
      {
        Dom.setAttribute(a, "_X_hasCrossBoundaryMarkup", "y")
        Dom.setAttribute(b, "_X_hasCrossBoundaryMarkup", "y")
      }



      /****************************************************************************************************************/
      /* Determines whether a given node should be regarded as containing canonical text, or being a canonical text
         node.  Actually, perhaps that is slightly misleading.  This method is used only when attempting to place a
         verse eid node, where basically we can move it towards the start of the node list so long as any nodes we
         skip over are definitely non-canonical.

         Returns true if ...

         - The node is a non-whitespace text node and is _not_ under a node whose contents we know a priori will always
           be non-canonical.

         - The node contains a verse node.  (This is a late addition -- it caters for the situation where we have an
           empty verse within, say, a para.  We want to treat empty verses as being canonical -- or more to the point,
           we can't move the eid for a given verse back past the sid, so we therefore also can't traverse anything
           which contains the sid.)

         - Any text node under the given node is canonical.
       */

      fun containsCanonicalText (node: Node): Boolean
      {
        if (Dom.isTextNode(node) && !Dom.isWhitespace(node) && !Usx.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node)) return true
        if (null != Dom.findNodeByName(node, "verse", false)) return true
        return Dom.findAllTextNodes(node).filter { !Dom.isWhitespace(it) }.any { !Usx.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(it) }
      }


      /****************************************************************************************************************/
      /* I take whitespace as skippable, plus any empty para, and any kind of tag known to be non-canonical, except that
         I _think_ we want to treat notes as not skippable (ie we want to retain them _inside_ verses). */

      fun canBeRegardedAsNonCanonical (node: Node): Boolean
      {
        if (Dom.isWhitespace(node)) return true // You can skip whitespace.
        val nodeName = Dom.getNodeName(node)
        if ("para"  == nodeName && !node.hasChildNodes()) return true // You can skip empty paras.
        if ("verse" == nodeName) return false // You can't skip notes.
        if ("note"  == nodeName) return false // You can't skip notes.
        if (Usx.isInherentlyNonCanonicalTag(node) || !containsCanonicalText(node)) return true // You can skip non-canonical tags, or any tag which has no canonical text nodes below it.
        return false
      }



      /****************************************************************************************************************/
      val verseEnd = Dom.createNode(m_Document, "<verse eid='$id'/>") // The new node.



      /****************************************************************************************************************/
      /* Don't be put off by the name.  This initial setting isn't a putative insertion point, but we will progressively
         refine things. */

      var putativeInsertionPoint = nextVerseSid



      /****************************************************************************************************************/
      /* For the remaining discussion, let's simplify the job of isSkippable, and imagine it simply looks for a
         node which is inherently canonical, which is of interest, because we can't place the eid before canonical
         text.  So we start off by running left across the siblings looking for such a node.  If we find one, then
         that's the place of interest (but will need further refinement).  If we don't find one (ie there are no
         blockages left of where we are), then we move up a level, and work left from that point, and so on. */

      while (true)
      {
        val p = Dom.getPreviousSiblingNotSatisfying(putativeInsertionPoint, ::canBeRegardedAsNonCanonical)

        if (null != p) // We've found a place we can't go beyond.
        {
          putativeInsertionPoint = p
          break
        }

        //if ("_X_chapter" == Dom.getNodeName(putativeInsertionPoint.parentNode)) // /Don't try to get above chapter level.)
        //  break

        putativeInsertionPoint = putativeInsertionPoint.parentNode
      }



      /****************************************************************************************************************/
      /* putativeInsertionPoint is now pointing at a node which we can't skip _past_, but we may be able to work our
         way down _into_ it, so long we stay to the right of any canonical content.  However, given that the whole
         point of what we're doing here is to try and get the sid and eid as siblings, there's no point in trying this
         at all unless the sid is a descendant of the putative insertion point. */

     if (Dom.hasAsAncestor(sidWhoseEidWeAreCreating, putativeInsertionPoint))
     {
       var improvedInsertionPoint = putativeInsertionPoint
       val dummyNode = Dom.createNode(m_Document, "<TEMP/>")
       while (true)
       {
         if (Dom.isSiblingOf(improvedInsertionPoint, sidWhoseEidWeAreCreating)) break
         if (Usx.isInherentlyNonCanonicalTag(improvedInsertionPoint)) break
         if (!improvedInsertionPoint.hasChildNodes()) break
         improvedInsertionPoint.appendChild(dummyNode)
         //if (!canBeRegardedAsNonCanonical(improvedInsertionPoint.lastChild)) break
         val p = Dom.getPreviousSiblingNotSatisfying(improvedInsertionPoint.lastChild, ::canBeRegardedAsNonCanonical) ?: throw StepException("!!!!")
         Dom.deleteNode(dummyNode)
         improvedInsertionPoint = p
       }

       if (Dom.isSiblingOf(improvedInsertionPoint, sidWhoseEidWeAreCreating))
       {
         Dom.insertNodeAfter(improvedInsertionPoint, verseEnd)
         return
       }
     }



      /****************************************************************************************************************/
      /* Nowhere good to put it, so may as well leave it at the top of the structure until experience teaches us there's
         something better we can do. */

      if (Dom.hasAsAncestor(nextVerseSid, putativeInsertionPoint))
        Dom.insertNodeBefore(putativeInsertionPoint, verseEnd)
      else
        Dom.insertNodeAfter(putativeInsertionPoint, verseEnd)

      if (!Dom.isSiblingOf(sidWhoseEidWeAreCreating, verseEnd))
        flagFailure(sidWhoseEidWeAreCreating, verseEnd)
    }


    /******************************************************************************************************************/
    /* I imagine it's unlikely we'll have verses which start inside a char node, but if we do I think it's ok to split
       the char node -- to end it before the verse and resume it immediately afterwards. */

    private fun splitEnclosingCharNodes ()
    {
      Dom.findNodesByName(m_Document, "verse").filter { "char" == it.parentNode.nodeName } .forEach { splitEnclosingCharNode(it) }
    }


    /******************************************************************************************************************/
    private fun splitEnclosingCharNode (verse: Node)
    {
      /****************************************************************************************************************/
      val parent = verse.parentNode
      val siblings = Dom.getSiblings(verse)
      val versePos = Dom.getChildNumber(verse)



      /****************************************************************************************************************/
      if (0 == versePos)
      {
        Dom.deleteNode(verse)
        Dom.insertNodeBefore(parent, verse)
        return
      }



      /****************************************************************************************************************/
      if (siblings.size - 1 == versePos)
      {
        Dom.deleteNode(verse)
        Dom.insertNodeAfter(parent, verse)
        return
      }



      /****************************************************************************************************************/
      val pre = parent.cloneNode(true)
      val post = parent.cloneNode(true)
      Dom.getChildren(post).subList(0, versePos).forEach { Dom.deleteNode(it) }
      Dom.getChildren(pre).subList(versePos, siblings.size).forEach { Dom.deleteNode(it) }
      Dom.insertNodeBefore(parent, pre)
      Dom.deleteNode(verse)
      Dom.insertNodeBefore(parent, verse)
      Dom.insertNodeAfter(parent, post)
      Dom.deleteNode(parent)
    }




    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /**                                                                                                              **/
    /**                                                    Tables                                                    **/
    /**                                                                                                              **/
    /******************************************************************************************************************/
    /******************************************************************************************************************/

    /******************************************************************************************************************/
    /* At the risk of sounding like a cracked record, tables are yet another area of significant complication, but need
       to be addressed because of the risk of cross-boundary markup.

       Tables are almost always large enough to span several verses (the one recent example I've seen where this was not
       the case arose from a mistaken attempt on the translators to find a way of introducing blank lines between the
       rows of what would otherwise naturally have been one large table).

       And the trouble with this is that as a consequence there are almost always problems in placing verse boundaries
       so as to avoid cross-verse-boundary markup.  And the trouble with _that_, if you get it wrong (and you _will_
       get it wrong, because in fact there is no obvious way of getting it _right_) is that you risk breaking osis2mod
       and possibly reversification (although I suspect reversification is unlikely to prove to be a victim, because I
       don't think the kinds of places where you have tables are likely to be places to which reversification applies).

       All of this is compounded by the fact that tables have not really been thought through, either in USX or in OSIS,
       and indeed the OSIS standard pretty much admits as much -- 'OSIS provides only very rudimentary tables'.  In
       fact, going beyond this, the fact that the interaction of tables and verses has not been considered is pretty
       much borne out by the fact that neither in the USX reference manual nor in the OSIS one do the examples of
       tables actually contain verse boundaries.

       Oh yes: and if, in the end, you decide you _do_ want to use tables, you also have to bear in mind that the OSIS
       reference manual assumes you will need to have recourse to user-defined attributes (which we do).  Which
       probably makes modules unusable by third parties -- plus the user-defined attributes we rely on ourselves don't
       seem to be documented anywhere, so the best we can do is build upon what has been done in previous modules, along
       with a dose of guesswork.

       Which leaves a problem, because real-world tables _do_ actually cross verse boundaries.

       To date, we have tried two different approaches to handling tables, plus some experiments which proved
       unsatisfactory.

       To deal with the experiments first, these were intended to address the situation (which I believe to be quite
       common) that a single row represents a single verse.  To this end I tried enclosing the row within a sid/eid
       pair; and as an alternative I tried enclosing the _contents_ of the row within such a pair.  In both cases,
       though, the formatting of the table is lost -- ie the columns cease to be of fixed width.  And I have therefore
       not pursued these further.

       One effective alternative (and the one which for a long time was all we had) is to remove all of the verse tags
       from within the table (plus doing some fiddly things at the start and end of the table, to avoid cross-
       boundary markup there), and to replace them with plain-text markers within the canonical text -- something like
       [12] -- to indicate where the boundaries originally fell.  And then finally we turn the verse which preceded the
       table into one massive elision covering the entire table.

       This definitely works, and makes it possible to retain the table format.  However, it comes with a number of
       problems.  Bearing in mind that our standard treatment of an elision is to expand it into individual verses,
       all of which bar the last are empty, with the last one containing the full text, the result is something which
       _looks_ odd.  It also breaks the vocabulary feature, in that hovering over a verse number gives the vocab which
       that verse _ought_ to have under normal circumstances, but this no longer tallies with what the verses actually
       contain.  Cross-references into the table now point either to empty verses or to the entire content of the table.
       And if reversification is applied to any of the verses making up the table, it now points to verses which don't
       look as it expects them to.

       Latterly, we have looked at an alternative, which involves adding a few non-breaking spaces at the end of each
       cell on a row bar the last, and then removing the table markup entirely.

       This approach overcomes all of the issues with the elided approach, but at the cost of once again losing the
       'tabularity' -- the data now comprises simply what was the first column of a given row, followed by a few spaces
       and then what was the second column.  (Which in fact makes it no better than the alternative experiments which I
       rejected.)

       There are also, to my mind, limits to how effective this can be.  Because columns no longer line up, it is more
       difficult to read (and certainly more difficult to see how data rows stack up with heading rows, if there are
       any).  To my mind, a table with more than two columns does not really work if rendered this way, because it is too
       difficult to see how the various elements relate to one another; and the heading is of only limited value.

       This finally moves us on to the implementation ...

       In a few cases, we may have tables which do not contain any verse boundaries.  Given that tables are usually
       relatively large, the lack of verse boundaries almost certainly does not indicate that the table _really_ fits
       within a single verse; rather it probably indicates that the translators have already applied elision, such that
       the content of the various individual verses making up the table has been moved into a single verse.  In this
       case, I do indeed render the table as a table -- although with the caveat that the lack of documentation on the
       attributes we use on table elements means that I may not be able to get things quite right.

       Where a table _does_ contain verse boundaries, I have provided two alternative implementations here -- the
       'elision' approach, and the 'table flattening' approach, both mentioned above.

       With the elision approach, the entire content of the table goes into the final verse in the table, and this
       revised content contains no verse tags (although I do add visible markers to indicate to the reader where the
       boundaries fell).  Prior to this verse, I insert empty verses (empty but for a footnote explaining what is going
       on) to reflect the verses which no longer participate in the table.

       With the table flattening approach, I proceed exactly as mentioned above -- I append non-breaking spaces to all
       cells bar the last in a given row (so as to give some spacing between items which would otherwise have constituted
       the columns on the row), and then remove the table markup altogether, turning each row into a single line.

       Which of these two options is used is selected by a compile-time flag -- I can see no value in making the choice at
       run time, because the configuration options are complicated enough without adding to them.

       As regards new attributes and tags ...

       - _X_tableWithNoContainedVerseTags is added to table tags in the fortuitous circumstances where there are no verse
         tags within the table.

      - _X_generatedElisionToCoverTable is added to sids where we have had to create an elision to cover the entire
        table.

      - _X_generatedReason='_X_emptyVerseForElisionRequiredByTableProcessing' is added to sids where we have had to create an
        elision and are adding an empty verse as part of that elision.  In this case, we also add
        _TEMP_partOfGroupContainingEmptyVerses='n', which is picked up by later processing to generate the standard
        content for an 'empty' verse.
  */

  /****************************************************************************/
  private fun restructureTablesMain ()
  {
    /**************************************************************************/
    /* I imagine there is no guarantee within a single text that all tables
       contain verse tags, or that all tables do not contain verse tags.
       I therefore have to reckon with the possibility that I may have to apply
       'complicated' processing to some tables, but not to others.

       The code immediately below identifies those tables which do _not_ contain
       verse markup, and changes their tag name.  This means that when I come
       to look for tables requiring complicated processing, I shan't find these
       'nice' tables, and will therefore leave them alone.  Later I shall
       actually need to apply a little special processing even to 'nice' tables,
       and I now have the wherewithal to identify them. */

    fun markTablesWhichDoNotContainVerseTags (tableNode: Node)
    {
      if (Dom.findNodesByName(tableNode, "verse", false).isEmpty())
        recordTagChange(tableNode, "_X_tableWithNoContainedVerseTags", null, "FYI only")
    }


    Dom.findNodesByName(m_Document, "table").forEach { markTablesWhichDoNotContainVerseTags(it); }




    /**************************************************************************/
    /* Flatten or elide any tables which need it. */

    run {
      val applyComplexConversion = @Suppress("KotlinConstantConditions") if ("Elide" == C_ConfigurationFlag_PreferredTableConversionType) ::restructureTablesConvertToElidedForm else ::restructureTablesConvertToFlatForm
      Dom.findNodesByName(m_Document, "table").forEach { applyComplexConversion.invoke(it) }
    }



    /**************************************************************************/
    /* If we temporarily changed tag names above, we have some 'proper' tables
       which we are retaining.  The main thing we need to do with these is to
       rename them back to 'table' again so later processing can pick them up.
       In addition, it seems like a good idea to boldface any heading cells.  And
       experience shows that Sword does not leave any spaces between columns,
       so I add some here.  I also insert a blank before the table, although
       I'm less clear as to whether this will _always_ be a good thing. */

    run {
      fun appendSpaces (cell: Node)
      {
        val textNode = Dom.createTextNode(m_Document, "&nbsp;&nbsp;&nbsp;")
        cell.appendChild(textNode)
      }

      fun processRowContent (rowNode: Node)
      {
        val cells = Dom.findNodesByName(rowNode, "cell", false)
        cells.subList(0, cells.size - 1). forEach { appendSpaces (it) }
      }

      fun restoreAndImproveTable (tableNode: Node)
      {
        val rows  = Dom.findNodesByName(tableNode, "row", false)
        val cells  = Dom.findNodesByName(rows[0], "cell", false) // Header.
        cells.forEach { restructureTablesEmboldenTableHeaderCell(it) }
        rows.forEach { processRowContent(it) }
        Dom.setNodeName(tableNode, "table")

        val blankLineA = Dom.createNode(m_Document, "<para style='b'/>") // Need two of these to end up with one blank line when rendered.
        val blankLineB= Dom.createNode(m_Document, "<para style='b'/>")
        Dom.insertNodeBefore(tableNode, blankLineA)
        Dom.insertNodeBefore(tableNode, blankLineB)
      }

      Dom.findNodesByName(m_Document, "_X_tableWithNoContainedVerseTags").forEach { restoreAndImproveTable(it) }
    } // run
  }


  /****************************************************************************/
  /* The latest rather simple-minded approach to handling tables is kinda
     predicated upon the tables not being too complicated.  We handle all tables
     the same way no matter how complicated, but it may be useful to issue
     warnings where we think that may not have been entirely successful. */

  private fun restructureTablesCheckContentOk (table: Node): Boolean
  {
    /**************************************************************************/
    val msgs: MutableMap<String, String>  = HashMap()



    /**************************************************************************/
    fun checkRow (row: Node)
    {
      /************************************************************************/
      /* If we have more than two cells per row, I suspect the non-tabular
         format, which the current method considers, will be too difficult to
         read.  Fewer than two kinda suggests this isn't really a table after
         all, but I have actually seen one with text like that, and don't want
         to rule it out.  If we _do_ have too many cells per row, it's only a
         warning -- I need to be able to process stuff anyway. */

      val cells = Dom.findNodesByName(row, "cell", false)
      if (cells.size > 2) msgs["Table: Number of cells in row is > 2: "] = cells.size.toString() + "."



      /************************************************************************/
      /* If it has a header row, the non-tabular format won't really work.
         I presume that the header is needed to make sense of the information
         which follows it, and in non-tabular format, it won't line up with the
         data. */

      val headerCell = cells.any { Dom.hasAttribute(it, "style") && Dom.getAttribute(it, "style")!!.startsWith("th") }
      if (headerCell) msgs["Table has header row."] = ""
    }



    /**************************************************************************/
    val verse = Dom.findNodeByName(table, "verse", false)
    val id = if (null == verse) null else if (Dom.hasAttribute(verse, "sid")) Dom.getAttribute(verse, "sid") else Dom.getAttribute(verse, "eid")
    val verseLocation = if (null == verse) "  Cannot give location because table does not contain any verse markers" else ("  At table containing verse sid or eid $id")
    Dom.findNodesByName(table, "row", false).forEach {checkRow(it) }
    msgs.keys.forEach { Logger.warning(it + msgs[it] + verseLocation + ".") }
    return msgs.isEmpty()
  }


  /****************************************************************************/
  /* The umpteenth attempt ...

     In this incarnation, I'm simply dropping the table altogether.  I arrange
     for each row to come out on a separate line (using para:b); and I add a
     few non-breaking spaces between cells.

     Of course this does mean that we lose the benefits of tables (ie justified
     columns), but then experience suggests that translators quite commonly do
     things which lose those benefits anyway.

     There are a few particular issues.  If a table has a header row, this is
     presumably because it is required in order to make sense of the content.
     However, with columns no longer lining up, this means that the header
     will be pretty meaningless.  And I suspect too that a table of more than
     two columns will be difficult to handle.  I report on these issues, but I
     still process things in the same way nonetheless. */

  private fun restructureTablesConvertToFlatForm (table: Node)
  {
    /**************************************************************************/
    restructureTablesCheckContentOk(table)



    /**************************************************************************/
    fun processCell (cell: Node)
    {
      val n = Dom.createTextNode(table.ownerDocument, "&nbsp;&nbsp;&nbsp;")
      cell.appendChild(n)
    }



    /**************************************************************************/
    fun processRowContent (row: Node)
    {
      val cells = Dom.findNodesByName(row, "cell", false)
      cells.subList(0, cells.size - 1).forEach { processCell(it) }
      cells.forEach { Dom.promoteChildren(it); Dom.deleteNode(it) }
      val n = Dom.createNode(table.ownerDocument, "<para style='b'/>")
      row.appendChild(n)
      Dom.promoteChildren(row)
      Dom.deleteNode(row)
    }



    /**************************************************************************/
    val rows = Dom.findNodesByName(table, "row", false)
    val cells = Dom.findNodesByName(rows[0], "cell", false)
    cells.forEach { restructureTablesEmboldenTableHeaderCell(it) }
    rows.forEach { processRowContent(it) }



    /**************************************************************************/
    val comment = Dom.createCommentNode(table.ownerDocument, "Was table")
    Dom.insertNodeBefore(table, comment)
    Dom.promoteChildren(table)
    Dom.deleteNode(table)
  }


  /****************************************************************************/
  /* The alternative way of handling complex tables.  The aim this time is to
     encapsulate the entire table within an elision, and then to insert some
     kind of marker wherever the verse boundaries originally came, just to
     show where they are.  If the table 'more or less' starts with a sid, then
     I move that sid out of the table and use that as the container for the
     elided verses.  Otherwise I locate the last verse prior to the table and
     use that.  Trouble is that there are still probably plenty of special
     cases for which I don't cater -- for example if the table contains
     headers before the first verse.  In this case, we'd want the headers to
     come outside the table, but we'd have no preceding verse to own the
     table. */

  private fun restructureTablesConvertToElidedForm (table: Node)
  {
    /**************************************************************************/
    var verseTags = Dom.findNodesByName(table, "verse", false) .toMutableList()
    var verseStarts = verseTags.filter { Dom.hasAttribute(it, "sid") }
    val verseEnds   = verseTags.filter { Dom.hasAttribute(it, "eid") } .toMutableList()
    val owningVerseSid: Node?



    /**************************************************************************/
    /* For error messages. */

    val location = if (verseStarts.isNotEmpty()) Dom.getAttribute(verseStarts[0], "sid")!! else Dom.getAttribute(verseEnds[0], "eid")!!
    val locationAsRefKey = Ref.rdUsx(location).toRefKey()



//    /**************************************************************************/
//    /* We know that verseTags cannot be empty, because this method is called
//       only for those situations where the table contains verse tags.  However,
//       we might still have the situation where the table contains only a
//       verse start or only a verse end.  In fact, I can't imagine this
//       happening; and since I don't want to be bothered with catering for it,
//       I will simply test for it and report it.
//
//       In fact, I'm going to go beyond this -- I'm going to assume that all
//       tables have at least two starts. */
//
//    if (verseStarts.size < 2)
//    {
//      Logger.error(locationAsRefKey,"Table containing fewer than two start tags at or about $location")
//      return
//    }
//
//    if (verseEnds.isEmpty())
//    {
//      Logger.error(locationAsRefKey, "Table containing only a start tag at or about $location")
//      return
//    }



    /**************************************************************************/
    /* Until we know otherwise, we'll close the elision after the table. */

    var closeElisionAfterNode = table



    /**************************************************************************/
    /* If the last verse tag within the table is a sid, it's fairly easy to
       cope if the eid more or less follows the end of the table -- we just
       move the eid back inside the table. */

    if (Dom.hasAttribute(verseTags.last(), "sid"))
    {
     val eid = Dom.findNodeByAttributeValue(m_Document, "verse", "eid", Dom.getAttribute(verseTags.last(), "sid")!!)!!
     if (Dom.isNextSiblingOf(table, eid, true))
      {
        Dom.deleteNode(eid)
        table.appendChild(eid)
        verseTags.add(eid)
        verseEnds.add(eid)
      }
      else // The eid isn't conveniently placed just after the table.
      {
        closeElisionAfterNode = eid
      }
    }



    /**************************************************************************/
    /* If the very first non-blank node in the table is a sid, move the sid
       immediately before the table so that the verse can contain the entire
       table. */

    if (Dom.isFirstNonBlankChildOf(table, verseStarts[0]))
    {
      owningVerseSid = verseStarts[0]
      Dom.deleteNode(owningVerseSid)
      Dom.insertNodeBefore(table, owningVerseSid)
      verseTags = verseTags.subList(1, verseTags.size)
      verseStarts = verseStarts.subList(1, verseStarts.size)
    }



    /**************************************************************************/
    /* If we haven't identified the owning verse, the owning verse must be the
       one which comes before the first verse start in the table, and we can
       find this based upon the sid. */

    else
    {
      val ref = Ref.rdUsx(Dom.getAttribute(verseStarts[0], "sid")!!)
      ref.setV(ref.getV() - 1)
      owningVerseSid = Dom.findNodeByAttributeValue(m_Document, "verse", "sid", ref.toString())
      if (null == owningVerseSid)
      {
        Logger.error(locationAsRefKey, "Table: Failed to find owning verse at or about $location")
        return
      }
    }



    /**************************************************************************/
    /* By this point, owningVerse points to the verse within which the table
       starts.  verseTags contains all of the verse tags _within_ the table,
       verseStarts all of the sids, and verseEnds all of the eids.  The last
       entry in verseEnds is an eid at the end of the table -- ie with no
       following canonical text. */



    /**************************************************************************/
    /* We're going to create an eid for the owning verse and position it at the
       other end of the table, so we no longer want its existing eid. */

    val existingEid = Dom.findNodeByAttributeValue(m_Document, "verse", "eid", Dom.getAttribute(owningVerseSid, "sid")!!)
    Dom.deleteNode(existingEid!!)



    /**************************************************************************/
    /* Change the sid of the owning verse to reflect the elision, and add an
       explanatory footnote. */

    val startOfElisionRef = Ref.rdUsx(Dom.getAttribute(owningVerseSid, "sid")!!)
    val endOfElisionRef   = Ref.rdUsx(Dom.getAttribute(verseStarts.last(), "sid")!!)
    val elisionRef = "$startOfElisionRef-$endOfElisionRef"

    Dom.setAttribute(owningVerseSid, "sid", startOfElisionRef.toString())
    Dom.setAttribute(owningVerseSid, "_X_generatedElisionToCoverTable", "y")
    val owningVerseFootnote = makeFootnote(m_Document, startOfElisionRef.toRefKey(), Translations.stringFormat(Language.Vernacular, "V_tableElision_owningVerse"))
    Dom.insertNodeAfter(owningVerseSid, owningVerseFootnote)



    /**************************************************************************/
    /* Insert an eid after the table to correspond to the owning verse. */

    val owningVerseEid = Dom.createNode(m_Document, "<verse eid='$startOfElisionRef'/>")
    Dom.insertNodeAfter(closeElisionAfterNode, owningVerseEid)
    if (closeElisionAfterNode !== table) Dom.deleteNode(closeElisionAfterNode) // We're closing after an eid which is no longer required.



    /**************************************************************************/
    /* Delete all eids within the table. */

    verseEnds.forEach { Dom.deleteNode(it) }



    /**************************************************************************/
    /* Replace all sids by visible verse-boundary markers. */

    fun insertVerseBoundaryMarker (sid: Node)
    {
      val sidText = Dom.getAttribute(sid, "sid")!!
      val markerText = Translations.stringFormat(Language.Vernacular, "V_tableElision_verseBoundary", Ref.rdUsx(sidText))
      val markerNode = Dom.createNode(m_Document, "<_X_verseBoundaryWithinElidedTable/>")
      markerNode.appendChild(Dom.createTextNode(m_Document, markerText))
      Dom.insertNodeAfter(sid, markerNode)
      Dom.deleteNode(sid)
    }

    verseStarts.forEach { insertVerseBoundaryMarker(it) }



    /**************************************************************************/
    /* At this juncture, we have the table entirely contained within a single
       verse, and the verse is marked up appropriately as an elision.  All that
       remains is to expand out the elision.  As a reminder, we encountered the
       table tag in the _first_ verse, so we want to have that recorded as the
       master, and we want the empty verses to follow it.  We almost have code
       to do this via expandElision, but that was set up on the assumption
       that empty verses would precede the master, and it's a bit fiddly to
       change that. */

    fun generateEmptyVerse (ref: Ref)
    {
      val (start, end) = EmptyVerseHandler.createEmptyVerseForElision(owningVerseSid.ownerDocument, ref.toString())
      Dom.insertNodeAfter(owningVerseEid, end)
      Dom.insertNodeAfter(owningVerseEid, start)
    }

    Dom.setAttribute(owningVerseSid, "_X_originalId", elisionRef)
    Dom.setAttribute(owningVerseSid, "_X_elided", "y")
    Dom.setAttribute(owningVerseSid, "_X_masterForElisionWithVerseCountOf", "" + verseStarts.size + 1)

    val elisionRange = RefRange.rdUsx(elisionRef).getAllAsRefs()
    elisionRange.subList(1, elisionRange.size).reversed().forEach { // We want to generate empty verses for everything bar the first verse, because the first verse is the master and contains all the text.
      generateEmptyVerse(it)
    }

    //Dbg.outputDom((owningVerseEid.ownerDocument))
  }


  /****************************************************************************/
  /* Wraps the content of a table header cell in boldface markup. */

  private fun restructureTablesEmboldenTableHeaderCell (headerCell: Node)
  {
    if (!Dom.hasAttribute(headerCell, "style")) return
    var style = Dom.getAttribute(headerCell, "style")!!
    if (!style.startsWith("th")) return
    style = "tc" + style.substring(2)
    Dom.setAttribute(headerCell, "style", style)
    val wrapper = Dom.createNode(m_Document, "<char style='bd'/>")
    Dom.getChildren(headerCell).forEach { wrapper.appendChild(it) }
    headerCell.appendChild(wrapper)
  }




    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /**                                                                                                              **/
    /**                                                  Lists                                                       **/
    /**                                                                                                              **/
    /******************************************************************************************************************/
    /******************************************************************************************************************/

    /******************************************************************************************************************/
    /* 'Lists' includes bullet point lists and poetry (which are represented in a rather similar way in USX).

       There is a lot we could do with lists, and at present I'm not doing any of it ...

       USX makes no provision for lists to be enclosed in the equivalent of the HTML <ul> tag.  To be compliant, our
       generated OSIS would require this.  However, it's fiddly to achieve; osis2mod works regardless; and having it
       actually makes the rendering worse (we get excess vertical whitespace around the list).  On this basis, currently
       I'm not bothering.  Unfortunately, this does give us the issue that we can't pass our OSIS to Crosswire, but
       we've decided that decent rendering trumps that currently.

       List items may also appear at a number of different 'levels'.  The USX spec stipulates very little here -- it
       does not require that a level 2 appears within a level 1, for instance, and does not require that you go up
       only a level at a time -- going from 1 to 3 appears to be perfectly ok.  I guess I could attempt to do something
       to address this -- introduce bracketing, insert missing levels and so on; but again it's awfully complicated and
       doesn't really buy us anything. */

    private fun encapsulateLists ()
    {
      encapsulateLists("io")
      encapsulateLists("is")
      encapsulateLists("li")
      encapsulateLists("pi")
      encapsulateLists("q")
    }


    /******************************************************************************************************************/
    /* Wraps poetry or bullet point lists within a container node (equivalent to HTML <ul>). */

    private fun encapsulateLists (@Suppress("UNUSED_PARAMETER") type: String)
    {
      // No implementation currently -- we don't encapsulate lists.
    }





    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /**                                                                                                              **/
    /**                         Validate assumptions upon which later processing relies                              **/
    /**                                                                                                              **/
    /******************************************************************************************************************/
    /******************************************************************************************************************/

    /******************************************************************************************************************/
    /* Must run before we set about changing tag names etc. */

    private fun validateInitialAssumptions ()
    {
        validateAssumptionsChaptersDirectChildrenOfUsx()
        validateAssumptionsNoNestedParas()
        validateAssumptionsNoElidedSubverses()
        validateAssumptionsNoCollectionsAsSids()
    }


    /******************************************************************************************************************/
    private fun validateAssumptionsChaptersDirectChildrenOfUsx ()
    {
        val usxNode = Dom.findNodeByName(m_Document, "usx")
        val chapters: List<Node> = Dom.findNodesByName(m_Document, "chapter")
        val haveChildWhichIsNotParentOfTopLevelNode = chapters.any { usxNode != Dom.getParent(it) }
        if (haveChildWhichIsNotParentOfTopLevelNode) Logger.error("Basic assumptions not met: All chapters were assumed initially to be children of the top level node.")
    }


    /******************************************************************************************************************/
    private fun validateAssumptionsNoCollectionsAsSids ()
    {
        fun checkSid(node: Node)
        {
            if (!Dom.hasAttribute(node, "sid")) return

            val sid = Dom.getAttribute(node, "sid")!!
            try
            {
                val rc = RefCollection.rdUsx(sid)
                if (1 != rc.getElements().size)
                    Logger.error("Invalid sid -- must not be collection: $sid")
            }
            catch (_: Exception)
            {
                Logger.error("Invalid sid -- must not be collection: $sid")
            }
        }

        Dom.findNodesByName(m_Document, "chapter").forEach { checkSid(it) }
        Dom.findNodesByName(m_Document, "verses").forEach { checkSid(it) }
    }


    /******************************************************************************************************************/
    private fun validateAssumptionsNoElidedSubverses ()
    {
        fun hasElidedSubverses(x: Node): Boolean
        {
            if (!Dom.hasAttribute(x, "sid")) return false
            val sid = Dom.getAttribute(x, "sid")!!
            if (!sid.contains("-")) return false
            val rc = RefCollection.rdUsx(sid)
            if (rc.getLowAsRef().hasS()) return true
            return rc.getHighAsRef().hasS()
        }

        val elidedNode = Dom.findNodesByName(m_Document, "verse").find { hasElidedSubverses(it) }
        if (null != elidedNode)
            Logger.warning( "Basic assumptions not met: Has elided subverses -- eg: " + Dom.toString(elidedNode))
    }


    /******************************************************************************************************************/
    private fun validateAssumptionsNoNestedParas ()
    {
        fun doIt (node: Node)
        {
            if ("verse" == Dom.getNodeName(node) && Dom.hasAttribute(node, "sid"))
                doNothing()
            else if ("para" == Dom.getNodeName(node) && "b" != Dom.getAttribute(node, "style") &&
                     null != Dom.getAncestorNamed(node, "para"))
            {
                Logger.error("Nested para: " + node.textContent)
                Logger.error("Basic assumptions not met: Has nested para.")
            }
        }

        Dom.findNodesByName(m_Document.documentElement, "para", false).forEach { doIt(it) }
    }





    /******************************************************************************************************************/
    /******************************************************************************************************************/
    /**                                                                                                              **/
    /**                                     Miscellaneous support functions                                          **/
    /**                                                                                                              **/
    /******************************************************************************************************************/
    /******************************************************************************************************************/

    /******************************************************************************************************************/
    /* The user may use the configuration data to specify the versification scheme to be used.  However, we may need to
       override anything they give under certain circumstances ...

       * If we are using the (currently experimental) STEP variant of osis2mod, we don't want to use any of the schemes
         built into osis2mod -- and indeed we want the scheme name to be unique.  For this, I prepend v11n on to the
         module name.

       * Otherwise if we're reversifying, we need the scheme to be either NRSV or NRSVA, depending upon whether or not
         we have DC books.

       Note that all of this may be up for grabs at present -- if we go with the STEP variant of osis2mod, we may well
       no longer be using reversification as originally anticipated, and the non-STEP branch below will be irrelevant.
  */

    private fun forceVersificationSchemeIfAppropriate ()
    {
        if (XXXOsis2ModInterface.usingStepOsis2Mod())
          ConfigData.put("stepVersificationSchemeCanonical", "v11n" + ConfigData["stepModuleName"], true)
        // Note that to keep osis2mod happy, the scheme names _must_ be all caps.
        else if (TextConverterProcessorReversification.runMe())
          ConfigData.put("stepVersificationSchemeCanonical", if (BibleStructure.UsxUnderConstructionInstance().hasAnyBooksDc() || ReversificationData.targetsDc()) "NRSVA" else "NRSV", true)
  }


    /******************************************************************************************************************/
    private fun initialise (rawUsxPath: String, document: Document)
    {
        /**********************************************************************/
        m_Document = document
        m_BookName = Dom.getAttribute(Dom.findNodeByName(m_Document, "book")!!, "code")!!
        reportBookBeingProcessed(document)
        if (null != m_SourceConversion) return



        /**********************************************************************/
        /* In some cases, we create the raw USX from some other format, such as
           VL.  In these we may be able to cut back on some of the processing
           here, because we can trust the input to be particularly simple or
           particularly reliable. */

        val doc = Dom.getDocument(rawUsxPath, retainComments = true)

        val comments = Dom.findCommentNodes(doc)
        if (comments.isEmpty())
        {
          m_SourceConversion = ""
          return
        }

        val x = comments[0].data
        val regex = Regex("(?i)SourceFormat:\\s*(?<fmt>.*?)\\.")
        val m = regex.find(x)
        m_SourceConversion = m?.groups?.get("fmt")?.value ?: ""
     }


    /******************************************************************************************************************/
    private fun panic ()
    {
      Dom.outputDomAsXml(m_Document, "C:/Users/Jamie/Desktop/panic.$m_BookName.usx", null)
      Logger.announceAllAndTerminateImmediatelyIfErrors()
    }


    /******************************************************************************************************************/
    private var m_BookName = ""
    private lateinit var m_Document: Document
    private var m_SourceConversion: String? = null
}

