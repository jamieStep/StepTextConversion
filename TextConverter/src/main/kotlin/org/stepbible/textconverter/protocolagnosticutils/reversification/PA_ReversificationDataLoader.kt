/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils.reversification

import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils.convertNumberToRepeatingString
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.butLast
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceBase
import org.stepbible.textconverter.protocolagnosticutils.PA
import java.io.File
import java.net.URL
import java.util.*


/******************************************************************************/
/**
 * Reads and stores reversification data and provides summary information about
 * it.
 *
 *
 *
 *
 *
 * ## About the data
 *
 * Each reversification row is made up of a number of named fields, and gives
 * rise to a single action.
 *
 * Not all rows are applicable to all texts.  Most rows have an associated
 * Test field, and the row applies only if all of the tests pass.  (Typical
 * tests include checking whether a given verse exists, whether a given verse is
 * the last in its owning chapter, or whether the content-length of two verses
 * stand in a particular relationship.)  The Test field may be empty, in which
 * case the test is assumed to pass.  Note that length tests applied to elided
 * verses always fail, because we have no way of determining the correct length
 * of an elided verse.
 *
 * All of this is taken into account automatically within the *process* method
 * here, so that by the time this method returns, the appropriate
 * reversification rows will have been selected for use by the caller.
 *
 * Note that we are concerned here *only* with selecting rows based upon
 * the Test field (whether blank or non blank).  Not all selected rows will do
 * anything on a particular run, but that is handled elsewhere.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
object PA_ReversificationDataLoader: PA()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection, wantBibleStructure = true)
    load(dataCollection)
  }


  /****************************************************************************/
  /**
  * Returns a list of all accepted rows.
  *
  * @return List of accepted rows.
  */

  fun getSelectedRows () = m_SelectedRows


   /****************************************************************************/
   /**
   * Returns a map of lists -- keyed on book number, with each entry being the
   * full list of ReversificationDataRow's for that book.
   *
   * @return Mapped reversification details.
   */

   fun getSelectedRowsBySourceBook (): Map<Int, List<ReversificationDataRow>> = getSelectedRows().groupBy { it.sourceRef.getB() }


  /****************************************************************************/
  /**
  * Returns a string representation of all accepted rows.  Intended mainly for
  * debugging and, if recorded within the STEP repository package for the
  * module, to make it possible to identify modules with certain
  * characteristics.
  *
  * @return List of accepted rows.
  */

  fun getSelectedRowsAsStrings () = m_SelectedRows.map { it.toString() }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Data-load                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This section loads the data.  At the end of processing, m_SelectedRows
     contains a full collection of ReversificationDataRow entries, representing
     the reversification rows which have been selected.

     I also canonicalise the data while doing this.  This caters for several
     issues:

     - In some cases, it seems to me that information has been placed in one
       field which should really appear in a different one.

     - I parse some of the more complex fields into constituent elements.

     - The data does not represent scripture references in an entirely
       consistent manner.  All use USX abbreviations, but their syntax is
       not that of USX.  I attempt to make things more uniform to make life
       easier for later processing.

     - Also there are some places which contain things which are really
       intended to be scripture references, but which don't quite fit the
       mould because they need to be extended to carry information which doesn't
       naturally fit into a scripture reference.  I try to sort these out, such
       that if I need to convert these entries to vernacular form, I stand a
       better chance of doing so.
   */

  /****************************************************************************/
  /**
  * Controls the overall loading of the data.
  *
  * @param dataCollection Data to which the reversification data is to be
  *   applied.
  */

  private fun load (dataCollection: X_DataCollection)
  {
    Rpt.report(1, "Reading reversification data and checking applicability.  Data is normally taken from the online repository, so this may take a moment.")
    load_1(dataCollection)
  }


  /****************************************************************************/
  /* Locates the relevant data within the input file and then reads it in and
     arranges to parse and canonicalise it. */

  private fun load_1 (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    /* Make commonly-used items readily available. */

    m_DataCollection = dataCollection
    m_BibleStructure = dataCollection.getBibleStructure(wantCanonicalTextSize = true)
    m_FileProtocol = dataCollection.getFileProtocol()
    m_RuleEvaluator = PA_ReversificationRuleEvaluator(dataCollection)



    /**************************************************************************/
    /* Read the full data from the location determined by the configuration
       data.  Preferably this will be our online data repository, where the
       data can be guaranteed to be up to date.  But you can also use a local
       file, for example if that will help with testing. */

    val dataLocation = ConfigData["stepExternalDataPath_ReversificationData"]!!
    if (!dataLocation.startsWith("http")) Logger.warning("Running with local copy of reversification data.")
    val rawData = (if (dataLocation.contains("http")) URL(dataLocation).readText() else File(dataLocation).readText()).split("\n")



    /****************************************************************************/
    /* Looks for a row containing a given piece of text.  Used to
       identify where the reversification data of interest to us starts and ends
       within the overall text. */

    fun findLine (data: List<String>, lookFor: String, startAt: Int): Int
    {
      val lookForLower = lookFor.lowercase()
      for (i in startAt..< data.size)
        if (data[i].lowercase().startsWith(lookForLower)) return i

      throw StepExceptionWithStackTraceAbandonRun("Guard row $lookFor missing from reversification data")
    }



    /**************************************************************************/
    /* I do at least make the assumption that the online data and any local
       copy will follow precisely the same format.  The data of interest is
       delimited by two markers, and then we need to remove from the resulting
       sublist any comment lines (which start with '#'), blank lines, lines
       starting '=' (which at one point were used to underline headings), and
       lines starting with a single quote (which latterly has been used at the
       of heading lines, so that they don't necessarily start with an equals
       sign as I think they used to).

       I keep a row count for debugging purposes -- it makes progress reporting
       more meaningful, and also makes it easier to determine which line is
       being processed if something goes wrong. */

    val ixLow = findLine(rawData, "#DataStart(Expanded)", 0)
    val ixHigh = findLine(rawData, "#DataEnd", ixLow)
    val filteredData = rawData.subList(ixLow + 1, ixHigh).map { it.trim() }.filterNot { it.startsWith('#') || it.isBlank() || it.startsWith('=') || it.startsWith('\'')  }
    Rpt.reportWithContinuation(level = 1, "Parsing reversification data (total of ${filteredData.size} rows) ...") { // Report progress in the same way as when processing books.
      var rowNumber = 0
      filteredData.forEach { loadRow(it, ++rowNumber) }
      Logger.announceAllAndTerminateImmediatelyIfErrors()
      debugOutputDebugData()
    }

    //Dbg.d(m_SelectedRows)
  }


  /****************************************************************************/
  /* Determines whether a row should be accepted for processing. */

  private fun acceptRowForProcessing (dataRow: ReversificationDataRow): Boolean
  {
    /**************************************************************************/
    //Dbg.d(dataRow.rowNumber == 22820)



    /**************************************************************************/
    /* I don't know what Concatenation rows are used for, but I've been told
       to ignore them. */

    if (dataRow["Action"].startsWith("Concatenation"))
      return false



    /**************************************************************************/
    /* The reversification data contains a few rows for 4Esdras.  We need to
       weed these out, because the USX scheme doesn't recognise this book.

       $$$ This may need to be changed at some point because we're trying to
       find ways to expand the list of books we accept -- although any such
       expansion will necessarily not be standards-compliant. */

    val sourceRef = dataRow["SourceRef"]
    if (sourceRef.contains("4es", ignoreCase = true))
      return false



    /**************************************************************************/
    /* For debug purposes it is often convenient to process only a few books.
       We need to ignore reversification data for any where the source
      reference is for a book which we are not processing. */

    if (!Dbg.wantToProcessBook(sourceRef.substring(0, 3)))
      return false



    /**************************************************************************/
    /* Does this book exist in the text? */

    if (!m_BibleStructure!!.bookExists(Ref.rdUsx(sourceRef.substring(0, 3) + " 1:1")))
      return false




    /**************************************************************************/
    return m_RuleEvaluator.rulePasses(dataRow.getField("Tests"), dataRow)
  }


  /****************************************************************************/
  /* Adds details of a single row, assuming it passes the relevant tests. */

  private fun loadRow (theRawData: String, rowNumber: Int)
  {
    /**************************************************************************/
    //Dbg.d("$rowNumber: $theRawData")



    /**************************************************************************/
    if (rowNumber == 1000 * (rowNumber / 1000))
      Rpt.reportAsContinuation(rowNumber.toString())



    /**************************************************************************/
    /* Fields in the reversification row are tab-separated.  Split them out, and
       create a ReversificationDataRow to hold the data.  The slightly odd code
       below works as follows:

       If we haven't yet initialised the column map, we can take it that the
       data we have read is correct -- we take it as-is, split it out, and
       that gives us all of the column names.

       If we _have_ initialised the column map, we may, on occasion, have a
       truncated row, if the reversification data should end with blank fields
       but they have been missed off.  In this case I add a load of trailing
       tabs to give the impression that the data is longer than it actually
       was, so that I'm guaranteed to have all the fields I need. */

    val rawData = theRawData.replace("_", "")
    val fields = (if (ReversificationDataRow.headersInitialised()) rawData + "\t\t\t\t\t\t\t\t" else rawData).split("\t").map { it.trim() }.toMutableList()
    val dataRow = ReversificationDataRow(rowNumber)
    dataRow.fields = fields



    /**************************************************************************/
    /* If this row is actually the header row, we merely want to record what
       the headers are and map them to field numbers, but not store the row as
       if it were a data row. */

    if (!ReversificationDataRow.headersInitialised())
    {
      var n = -1
      fields.forEach { ReversificationDataRow.setHeader(it, ++n) }
      return
    }



    /**************************************************************************/
    /* We want to ignore rows which fail their applicability test, rows for
       books we aren't processing on this run, etc. */

    val accept = acceptRowForProcessing(dataRow)

    if (accept)
    {
      canonicaliseAndCorrectData(dataRow)
      m_SelectedRows.add(dataRow)
    }



    /**************************************************************************/
    /* May be useful for debugging. */

    if (accept)
      debugRecordReversificationRowAccepted(dataRow)
    else
      debugRecordReversificationRowRejected(dataRow)
  }


  /****************************************************************************/
  /* There are some inconsistencies / infelicities in the reversification data,
     and processing is easier if these are sorted out.  This method leaves
     the 'fields' array of dataRow unchanged.  However, the action, sourceRef
     and standardRef fields may be set using revised copies of these fields,
     so as to correct the inconsistencies.

     This does mean that these specific individual fields may not exactly
     mimic the content of the 'fields' array, but on the other hand it means
     that the 'fields' array is still as read from the reversification data,
     which may simplify debugging.
   */

  private fun canonicaliseAndCorrectData (dataRow: ReversificationDataRow)
  {
    /**************************************************************************/
    //Dbg.dCont(dataRow.toString(), "Est.3:13!0")



    /**************************************************************************/
    /* Get the canonical form of the action name.  This is the form as supplied,
       but with the '*' used to mark Moves removed, spaces suppressed and
       converted to lower case.

       (In fact, we no longer make much use of the Action field, so this
       processing is somewhat superfluous.  We do need to be able to recognise
       IfEmpty rows, so to that extent the following code para is useful, but
       that's about as far as it goes.) */

    dataRow["Action"] = dataRow["Action"].replace(" ", "").lowercase()
    val x = dataRow["Action"].replace("*", "").replace(" ", "").lowercase()
    dataRow.action = x



    /**************************************************************************/
    val titlePseudoVerseNumber = RefBase.C_TitlePseudoVerseNumber.toString()
    fun convertToUsx (s: String): Ref
    {
      val asString = s.replace(".", " ").lowercase().replace("title", titlePseudoVerseNumber).replace("!0", "").replace("!", "")
      return Ref.rdUsx(asString)
    }

    dataRow.sourceRef = convertToUsx(dataRow["SourceRef"])
    dataRow.standardRef = convertToUsx(dataRow["StandardRef"])



    /**************************************************************************/
    setCalloutAndFootnoteLevel(dataRow)
    setAncientVersions(dataRow["Ancient Versions"])
  }


  /****************************************************************************/
  /* New format callout / footnote processing.

     The callout (NoteMarker) column contains quite a lot of information, and
     at one time this was all relevant to us.  However, since we gave up the
     idea of physically restructuring the text during the conversion process,
     most of the information is no longer relevant -- all we need is to
     recognise things like Nec, Acd, etc, which tell us the kind of audience
     different footnotes are relevant to. */

  private const val C_FootnoteLevelNec = 'N'
  private const val C_FootnoteLevelAcd = 'A'
  private const val C_FootnoteLevelOpt = 'O'
  private const val C_FootnoteLevelInf = 'I'

  private fun setCalloutAndFootnoteLevel (dataRow: ReversificationDataRow)
  {
    /**************************************************************************/
    val x = dataRow["NoteMarker"]
    when (x.trim().substring(0, 2).lowercase())
    {
      "ne" -> dataRow.footnoteLevel = C_FootnoteLevelNec // If footnotes are permitted at all, this should always be output, regardless of whether this is an academic or non-academic run.
      "ac" -> dataRow.footnoteLevel = C_FootnoteLevelAcd // Output on academic runs only.
      "op" -> dataRow.footnoteLevel = C_FootnoteLevelOpt // Not exactly sure what this means, but it is used in a slightly complicated way.
      "in" -> dataRow.footnoteLevel = C_FootnoteLevelInf // Used selectively on empty verses.
      else -> Logger.error("Reversification invalid note level: " + x[0])
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Utilities                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * The reversification data uses its own formats for references.  It's
   * convenient to convert this to USX for processing because I already have
   * code to handle USX.  The purpose of this present routine is not to
   * return an actual USX reference / range / collection, but to reformat
   * its input so that it follows USX formatting.
   *
   * Unfortunately this is complicated by the variety of formats which appear
   * in the data.  I give details below of all of the various formats, so
   * that they are documented.  However, this routine should be called only
   * for references which appear in the VersificationNote and AncientVersions
   * columns.
   *
   * - One thing which is common -- book abbreviations throughout are USX
   *   abbreviations.  It looks as though the list will be extended at some
   *   point (although how useful this will be, I don't know, given that anyone
   *   supplying us with text will be limited to the standard USX list, or else
   *   will have to make up their own abbreviations in order to extend the list).
   *
   * - SourceRef and StandardRef look like Gen.1:2!a -- ie book / chapter
   *   separator is a period, chapter / verse is a colon, verse / subverse is an
   *   exclamation mark.  Canonical titles are shown as eg Psa.51:Title; and
   *   subverse zero is shown as !0.
   *
   * - The Tests column basically follows the same format as SourceRef and
   *   StandardRef, except that subverses are shown as .1, .2, etc rather than
   *   !a and !b.  It does not contain any references to subverse 0 or to
   *   canonical titles.  This means that but for the period chapter / verse
   *   separator, references here follow USX format.
   *
   * - This then leaves the other columns -- ie the Ancient versions and the
   *   VersificationNote column. (And theoretically also the ReversificationNote
   *   column, but we don't use that.)
   *
   * - By contrast with the columns discussed previously, these may contain
   *   collections and ranges as well as single refs.  And they may also appear
   *   in 'difficult' contexts, because it is sometimes necessary to express the
   *   idea of there being alternative verses etc at a given point.  I rely upon
   *   processing elsewhere to take care of most of the difficult context stuff,
   *   so that here we are concerned purely with the parseability of the
   *   references themselves.
   *
   * - Here, the basic reference looks -- once again -- like Gen.1:2.  Subverses
   *   are shown as a, b, c, etc -- but this time with no preceding separator.
   *   And reference may also be made to subverse zero, using an asterisk for
   *   the purpose -- so you might have something like Gen.1:2*.
   *
   * - Things are further complicated at the time of writing by the fact that
   *   in some cases the format is actually incorrect, so I have to correct it
   *   before I set about parsing things.
   *
   *
   * Having said all of which, DIB has suggested that I don't bother attempting
   * to parse the AncientVersion information, on the grounds both that
   * references there are difficult to parse, and that this information is being
   * presented only to an academic audience, who will, in any case, be able
   * to speak English.  I have retained here the code which I wrote to handle
   * this, just in case we decide in future to do so after all, although there
   * is no guarantee that it was able to cope in all circumstances, and
   * certainly no guarantee that I will keep it up to date in order to follow
   * any future changes which are made to the data.
   *
   *
   * @param theStepRef Reference in STEP format.
   * @return String representation of an equivalent reference in USX format.
   *         Note that this may differ from the original in respect of
   *         whitespace, separators, etc, but since we need it only for
   *         parsing, that's not an issue.
   */

  fun usxifyFromStepFormat (theStepRef: String): String
  {
    /**************************************************************************/
    //Dbg.d(theStepRef)


    /**************************************************************************/
    /* Get the reference string into canonical form.  The input may contain
       various range and collection separators, in part because the separators
       commonly differ according to whether the references at either side of
       them are in the same chapter or not.  It is convenient to reduce these
       to standard form, since my parser isn't worried about such niceties.
     */

    var stepRef = theStepRef.replace(",", ";") // Collection separators.
                            .replace("--", "-")       // Range separators.
                            .replace("–", "-")        // Range separators.
                            .replace("\\s*\\+\\s*", ";") // Turns eg 9:2+8:30 into 9:2; 8:30.  I'm not sure if that's right, but it's the only thing which makes sense.
                            .replace("([1-9A-Za-z][A-Za-z][A-Za-z]\\.)".toRegex()) { it.value.replace(".", " ") } // Book / chapter separator.



    /**************************************************************************/
    /* Not too clear what to do with subverse zero markers.  When setting up the
       reference handling, I didn't anticipate having anything legit with a
       value of zero, and so I can't represent subverse zero at present.

       In fact, the reversification data doesn't appear to be at all consistent
       as to whether it represents subverse zero explicitly or not, so for the
       time being, I'm simply going to ignore subverse zero -- kind of.  If we
       have Gen.1:2*, I convert it to Gen.1:2.  If we have Gen.1:2*a, I
       convert it to Gen.1:2;a. */

    stepRef = if (stepRef.endsWith("*"))
      stepRef.butLast()
    else
      stepRef.replace("*", ";")



    /**************************************************************************/
    fun processCollectionElement (elt: String) = elt.split("-").joinToString("-"){ usxify1(it) }
    return stepRef.split(";").joinToString(";"){ processCollectionElement(it) }
  }


  /****************************************************************************/
  /* This deals with a single reference, and is intended to be called on
     columns other than sourceRef and standardRef (which, as noted above, may
     have an odd format, but the format there is irrelevant, because we don't
     need to be able to parse them).

     This leaves us with just two different formats to cater for.  In the Tests
     column, subverses are numeric (and may include subverse zero.  Elsewhere,
     subverses are alphabetic, and subverse zero is represented by an asterisk.

     For the sake of uniformity (and to bring things into line with USX), I
     convert to alphabetic form.  I do retain the asterisk as a subverse zero
     marker, though, and that _isn't_ in line with USX, which does not have a
     subverse zero. */

  private val C_Regex_NumericSubverse = """(.*?)(\.\d+)?$""".toRegex()
  private fun usxify1 (theStepRef: String): String
  {
    val match = C_Regex_NumericSubverse.matchEntire(theStepRef) ?: throw StepExceptionWithoutStackTraceBase("Reversification data: Invalid ref: $theStepRef.")
    val (mainPart, subverse) = match.destructured
    val revisedSubverse = if (subverse.isEmpty()) "" else {
      val x = subverse.substring(1).toInt()
      if (0 == x)
        "*"
      else
        convertNumberToRepeatingString(x, 'a', 'z')
    }

    return mainPart + revisedSubverse
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                      Private -- Ancient versions                       **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Ancient version information is potentially very complicated.  It is
     made up of a combination of fixed text and references, with some of the
     references being in very complicated formats because it is necessary
     to include information about variants etc.

     At one time I had code to handle all of this in such a way that we could
     translate things into the vernacular where vernacular information was
     available.  However, it was very difficult to cope with the various
     formats, and DIB has suggested that there is little point in worrying
     about translation, because the ancient version information is made
     available only to an academic audience, and the assumption is that such
     an audience will cope with English.

     I have retained the original complex code here, but at the time of
     writing I am no longer using it.  This has two particular corollaries.
     First, the code will no longer be kept up to date in the light of any
     changes to the data.  (I'm not 100% sure it could cope properly with
     all of the data even as things stood when we last used it, but it will
     probably drift further over time.)  And second, since I am no longer
     bothering to parse the data, any errors will not be apparent.
  */

  /****************************************************************************/
  /* You get one of these per tradition in the AncientVersions information. */

  internal class AncientVersionsTradition (theContent: String)
  {
    /**************************************************************************/
    override fun toString (): String
    {
      /************************************************************************/
      /* This first block deals with the main elements associated with a given
         tradition.  For example, the bit after the equals sign in

           Greek=12:18 / 12:17b+13:1a

         A slash is used quite commonly here to separate alternatives.  The
         above example is atypical, in that it also uses a plus-sign as a
         delimiter.

         The processing below is revoltingly messy, and even then is perhaps
         not as flexible as other aspects of the vernacular-related formatting,
         in that it assumes that a multi-element collection will always be
         shown in the same order as in the reversification data, and that it
         will be separated by delimiters in the same way as in the
         reversification data. */

      fun processMainElementRefCollection (refAsString: String): String
      {
        return if ("--" == refAsString)
          TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsNoReference")
        else
          TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsMainRefFormat", RefCollection.rd(usxifyFromStepFormat(refAsString), null,"v"))
      }

      fun processMainElementDelimiter (delim: String): String
      {
        return when (delim)
        {
          "/" -> TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsAlternativeRefsSeparator")
          "+" -> TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsJointRefsSeparator")
          else -> throw StepExceptionWithStackTraceAbandonRun("AncientVersions delimiter not handled: $delim")
        }
      }

      val delimitedMainElementsElements = m_MainElements.split("((?=^)|(?<=^))".replace("^", "[/+]").toRegex()).toMutableList() // Split with the delimiters shown in square brackets, and retain the delimiters.
      delimitedMainElementsElements.forEachIndexed { ix, content -> if ((ix and 1) == 0) delimitedMainElementsElements[ix] = processMainElementRefCollection(content) }
      delimitedMainElementsElements.forEachIndexed { ix, content -> if ((ix and 1) == 1) delimitedMainElementsElements[ix] = processMainElementDelimiter(content) }
      val mainEltsAsString = delimitedMainElementsElements.joinToString("")



      /************************************************************************/
      /* This section caters for anything within square brackets. */

      var equivalenceInformation = ""
      if (null != m_EquivalenceInformationReferenceCollection)
        equivalenceInformation = " " + TranslatableFixedText.stringFormat(m_EquivalenceInformationFormatString, m_EquivalenceInformationReferenceCollection!!)



      /************************************************************************/
      val tradition: String = TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_language$m_Tradition")
      return TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsTraditionFormat", mapOf("tradition" to tradition, "main" to mainEltsAsString, "equivalenceInformation" to equivalenceInformation))
    }


    /**************************************************************************/
    private var m_EquivalenceInformationReferenceCollection: RefCollection? = null
    private var m_EquivalenceInformationFormatString = ""
    private var m_MainElements = ""
    private var m_Tradition = ""


    /**************************************************************************/
    init
    {
       /************************************************************************/
       //Dbg.dCont(theContent, "/")



      /************************************************************************/
      /* Get rid of spaces, which only serve to complicate things and aren't
         of use to the processing. */

      var content = theContent.replace(" ", "")



      /************************************************************************/
      /* Split off the tradition. */

       var ix = content.indexOf("=")
       m_Tradition = content.substring(0, ix)
       content = content.substring(ix + 1)



      /************************************************************************/
      /* Remove any trailing bullet point.  Bullet points act as separators
         between traditions.  Ideally I'd have split traditions out by splitting
         at these separators, but unfortunately it looks as though in some cases
         a bullet point has been used to mark subverse zero.  (It _shouldn't_
         have been -- DIB has been using an Arabic Arabic numeral zero to mark
         subverse zero.  However, the two characters look almost identical, so
         it's easy to use the wrong one.) */

      if (content.last() == '\u2022') // Bullet point.
        content = content.substring(0, content.length - 1)



      /************************************************************************/
      /* Split off any trailing equivalence information (ie the bit in square
         brackets). */

      var equivalenceInformation: String? = null
      ix = content.indexOf("[")
      if (ix >= 0)
      {
        equivalenceInformation = content.substring(ix + 1).replace("]", "")
        content = content.substring(0, ix)
      }



      /************************************************************************/
      m_MainElements = content



      /************************************************************************/
      /* If we have equivalence information, turn it into an appropriate
         collection.  Life is made a bit easier here by the fact that we don't
         need to worry about slashes and double-dashes in the equivalence
         information, because there aren't any -- they're all pukka
         references. */

      if (null != equivalenceInformation)
      {
        when (equivalenceInformation!!.first())
        {
          '+' ->
           {
              m_EquivalenceInformationFormatString = TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_ancientVersionsEquivalencePlus")
              equivalenceInformation = equivalenceInformation!!.substring(1)
           }

           '=' ->
            {
              m_EquivalenceInformationFormatString = TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_ancientVersionsEquivalenceEquals")
              equivalenceInformation = equivalenceInformation!!.substring(1)
            }

            else ->
            {
              m_EquivalenceInformationFormatString = TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_ancientVersionsEquivalenceUndecorated")
              equivalenceInformation = equivalenceInformation!!.substring(1)
           }
        }

        m_EquivalenceInformationReferenceCollection = RefCollection.rdUsx(usxifyFromStepFormat(equivalenceInformation!!))
      }
    }
  }


  /****************************************************************************/
  /**
   * Converts the ancient version information into a vernacular string
   * containing vernacular references as far as this is possible.
   *
   * @return Ancient version information.
   */

   private fun ancientVersionsToString (traditions: List<AncientVersionsTradition>): String
   {
    val resElements: MutableList<String> = ArrayList()
    traditions.forEach { x -> resElements.add(x.toString()) }
    if (resElements.isEmpty()) return ""

    var res = resElements.joinToString(TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsTraditionSeparator"))
    res = TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersions", res)
    return res
  }


  /****************************************************************************/
  /* Converts the raw AncientVersion information into string format.

     Ancient version information looks like ...

       ( Hebrew=1:1 | Greek=3:16 | ... )   ['|' = bullet point]

     The references are complicated ...

     * They may be a simple reference, a reference range, or a collection.
       All of these are in STEP format with alphabetic subverses.

     * Or they may be a list of alternatives, separated by ' / ', each
       element itself being a reference, a range or a collection.

     * Or they may consist of --, indicating that there _is_ no reference.

     * And they may contain additional information in square brackets --
       information which may itself start with an equals sign or a plus sign
       (or with neither), the significance which I am currently unaware of.

     * The processing here splits these various bits out into
       AncientVersionsChunks, one per tradition.
   */

  private fun setAncientVersions (theText: String?): String
  {
    /**************************************************************************/
    return theText ?: "" // As noted elsewhere, at least pro tem we have decided not to parse and translate the ancient version information.



    /**************************************************************************/
    //Dbg.dCont(theText ?: "", "(Greek=1:14 / 1:14a)")



    /**************************************************************************/
    if (theText.isNullOrEmpty()) return ""



    /**************************************************************************/
    val traditions: MutableList<AncientVersionsTradition> = ArrayList()



    /**************************************************************************/
    var text = theText!!.substring(0, theText.length - 1).substring(1) // Get rid of the enclosing parens.



    /**************************************************************************/
    /* Some entries have things with equals signs in square brackets to
       indicate that the reference list is in some manner similar to another
       alternative.  This would get in the way of the processing below, so
       temporarily I replace the equals sign. */

    text = text.replace("[=", "[\u0001")



    /**************************************************************************/
    val languageTraditions = listOf(*text.split("(?=\\b(Hebrew=|Latin=|Greek=))".toRegex()).toTypedArray())
    languageTraditions.subList(1, languageTraditions.size).forEach { traditions.add(AncientVersionsTradition(it)) }
    return ancientVersionsToString(traditions)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                        Private -- debug support                        **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* stepDbgReversificationAnticipatedSourceType, if defined at all, should
     have one of the values English, Hebrew, Latin or Greek.

     The processing then expects rows whose SourceType contains the relevant
     string (case-insensitive) to be selected for processing, while others
     are not, and will output messages to that effect.  This isn't _quite_
     right (some rows are for specific variants of these like Latin2), but
     it will at least give us something at least vaguely useful for
     checking.
  */

  private val m_AnticipatedSourceType : String? by lazy { ConfigData["stepDbgReversificationAnticipatedSourceType"]?.lowercase() }


  /****************************************************************************/
  private class DebugData
  {
    var m_RowsAccepted: MutableList<ReversificationDataRow> = ArrayList() // All of the rows accepted.
    var m_RowsAcceptedButShouldNotHaveBeen: MutableList<ReversificationDataRow> = ArrayList()
    var m_RowsRejectedButShouldNotHaveBeen: MutableList<ReversificationDataRow> = ArrayList()
  }

  private val  m_DebugData = DebugData()


  /****************************************************************************/
  /* Hasn't been used recently.  At one stage I thought it useful to check how
     the selected rows stacked up against the rows we might have expected for
     a text which we knew a priori should probably follow, say, the Latin
     versification scheme.

     This runs only if we have recorded our anticipated source time; and given
     that it hasn't run for a long time, it may be that it no longer works, for
     all I know. */

  private fun debugOutputDebugData ()
  {
    if (null == m_AnticipatedSourceType) return
    m_DebugData.m_RowsAcceptedButShouldNotHaveBeen.forEach { x -> Logger.info("Reversification row accepted but perhaps should not have been: $x") }
    m_DebugData.m_RowsRejectedButShouldNotHaveBeen.forEach { x -> Logger.info("Reversification row rejected but perhaps should not have been: $x") }
    m_DebugData.m_RowsAccepted.forEach { x -> Logger.info("Reversification row accepted as anticipated: $x") }
  }


  /****************************************************************************/
  /* For debugging -- records details of which rows were accepted and
     rejected. */

  private fun debugRecordReversificationRowAccepted (dataRow: ReversificationDataRow)
  {
    /**************************************************************************/
    /* This flavour of debugging is relevant only where we are anticipating
       the text will conform reasonably closely to one of the common
       versification schemes like Latin, and have indicated as much. */

    if (null == m_AnticipatedSourceType) return



    /**************************************************************************/
    /* Ignore lines where the source ref relates to a book we don't have. */

   if (!m_DataCollection.getBibleStructure().bookExists(dataRow.sourceRef.getB())) return



   /**************************************************************************/
    val sourceType = dataRow["SourceType"].lowercase()
    val containsAnticipatedSource = sourceType.contains(m_AnticipatedSourceType!!)
    m_DebugData.m_RowsAccepted.add(dataRow)
    if (!containsAnticipatedSource)
      m_DebugData.m_RowsAcceptedButShouldNotHaveBeen.add(dataRow)
  }


  /****************************************************************************/
  /* For debugging -- records details of which rows were accepted and
     rejected. */

  private fun debugRecordReversificationRowRejected (dataRow: ReversificationDataRow)
  {
    /**************************************************************************/
    /* This flavour of debugging is relevant only where we are anticipating
       the text will conform reasonably closely to one of the common
       versification schemes like Latin, and have indicated as much. */

    if (null == m_AnticipatedSourceType) return



    /**************************************************************************/
    /* Ignore lines where the source ref relates to a book we don't have. */

   if (!m_DataCollection.getBibleStructure().bookExists(dataRow.sourceRef.getB())) return



   /**************************************************************************/
    val sourceType = dataRow["SourceType"].lowercase()
    val containsAnticipatedSource = sourceType.contains(m_AnticipatedSourceType!!)
    if (containsAnticipatedSource && Dbg.wantToProcessBook(dataRow.sourceRef.getB()))
      m_DebugData.m_RowsRejectedButShouldNotHaveBeen.add(dataRow)
  }


  /****************************************************************************/
  private lateinit var m_RuleEvaluator: PA_ReversificationRuleEvaluator
  private val m_SelectedRows: MutableList<ReversificationDataRow> = ArrayList(10000)
} // PA_ReversificationHandler





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                          ReversificationDataRow                          **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* A single row of the reversification data. */

class ReversificationDataRow (rowNo: Int)
{
  lateinit var action: String
           var ancientVersions = ""
  lateinit var fields: MutableList<String>
           var footnoteLevel = '?'
           val rowNumber = rowNo // Publicly accessible only for debugging.
  lateinit var sourceRef: Ref
  lateinit var standardRef: Ref

  companion object {
    private val m_Headers: MutableMap<String, Int>  = TreeMap(String.CASE_INSENSITIVE_ORDER) // Headers of reversification data file.
    fun headersInitialised () = m_Headers.isNotEmpty()
    fun setHeader (name: String, ix: Int) { m_Headers[name] = ix }
  }



  /****************************************************************************/
  fun getField (key: String): String { val ix = m_Headers[key]!!; return if (ix < fields.size) fields[ix] else "" }
  fun setField (key: String, value: String) { val ix = m_Headers[key]!!; fields[ix] = value }


  /****************************************************************************/
  override fun toString (): String
  {
    return "Row: " + rowNumber.toString() + " " +
      getField("SourceType") + " " +
      getField("SourceRef") + " " +
      getField("StandardRef") + " " +
      getField("Action") + " " +
      getField("NoteMarker")
  }
} // class ReversificationDataRow

operator fun ReversificationDataRow.get (fieldName: String): String { return this.getField(fieldName) }
operator fun ReversificationDataRow.set (fieldName: String, value: String) { this.setField(fieldName, value) }
