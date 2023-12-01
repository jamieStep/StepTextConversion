package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.Translations
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.shared.SharedData
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.net.URL
import java.util.*


/******************************************************************************/
/**
 * Tagging handler.
 *
 * Contains code to apply extended tagging to file.
 *
 * I could do this in either of two ways.  I could add relevant tags and
 * attributes to the USX as it makes its way through to becoming enhanced USX;
 * or I could apply the tagging direct to the OSIS file.
 *
 * There is, in fact, little benefit in applying it to the USX (aside from the
 * fact that I am more familiar with USX): the only advantage of doing so would
 * be if we were able to output tagged data in standard USX form, which we could
 * then perhaps offer to third parties where licensing conditions permit.  This we
 * cannot do, because USX does not support the necessary attributes.
 *
 * Applying it to OSIS makes sense, because for some texts all we actually have
 * available is OSIS, and these will have to be tagged directly anyway.  If we
 * are set up here to process OSIS, the code here can be used both to process
 * OSIS as part of a standard conversion from USX to Sword module, and also to
 * process OSIS supplied as the sole input for a module.
 *
 * @author ARA Jamieson
 */

object TextConverterTaggingHandler: TextConverterProcessorBase, ValueAddedSupplier()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Supplies a list of details appropriate for use as part of the StepAbout
  * setting.
  *
  * @return Details.
  */

  override fun detailsForStepAbout (): List<String>?
  {
    if (!m_StrongsCorrectionsApplied) return null
    return listOf(Translations.stringFormatWithLookup("V_AddedValue_RevisedStrongsTagging"))
  }


  /****************************************************************************/
  /**
  * Supplies a list of details appropriate for use as part of the Sword
  * config file administrative details.
  *
  * @return Details.
  */

  override fun detailsForSwordConfigFileComments (): List<String>?
  {
    if (!runMe()) return null
    val text = "StepAdminRevisedStrongsTagging: " + (if (m_StrongsCorrectionsApplied) "Yes" else "Not required")
    return listOf(text)
  }


  /****************************************************************************/
  override fun banner (): String
  {
    return "Tagging OSIS"
  }


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
    return SharedData.SpecialFeatures.hasStrongs() // This assumes that the only change we are making is to update Strong's.
  }



  /****************************************************************************/
  override fun process (): Boolean
  {
    ValueAddedSupplier.register("ExtendedTagging", this)
    handleStrongsCorrections()
    return true


//    describeDataFiles()
//    BibleStructure.OsisInstance().populateFromFile("C:\\Users\\Jamie\\RemotelyBackedUp\\Git\\StepTextConversion\\Texts\\Dbl\\Biblica\\Text_deu_XXX\\Osis\\deu_XXX_osis.xml", false)
//    getReversificationMappings()
//    TextConverterFeatureSummaryGenerator.setHaveAppliedExtendedTagging(true)
//    ConfigData["stepAddedValueExtendedTagging"] = "Yes"
//    return true
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                    Private -- applying tagging                         **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun handleStrongsCorrections ()
  {
    return
    val verncularAbbreviation = ConfigData["stepVernacularAbbreviation"]!!.lowercase()
    if ("nasb" in verncularAbbreviation || "lsb" == verncularAbbreviation)
    {
      StrongsCorrectionsForLockman.handleStrongsCorrections()
      return
    }
  }



  /**************************************************************************/
  /* I don't really _like_ polluting the converter with lots of stuff
     specific to individual text suppliers or texts, but until I have more of
     a handle on what's going on, this will have to do. */
     
  /**************************************************************************/
  private object StrongsCorrectionsForLockman
  {
    /************************************************************************/
    fun handleStrongsCorrections ()
    {
      readStrongsCorrectionFile()
      val doc = Dom.getDocument(StandardFileLocations.getOsisFilePath())
      val strongsNodes = Dom.findNodesByAttributeName(doc, "w", "lemma")
      if (strongsNodes.isEmpty()) return
      strongsNodes.forEach { handleStrongsCorrections(it) }
      if (m_StrongsCorrectionsApplied)
        Dom.outputDomAsXml(doc, StandardFileLocations.getOsisFilePath(), null)

      m_StrongsCorrections.clear() // No longer needed.
    }


    /**************************************************************************/
    private fun handleStrongsCorrections (node: Node)
    {
      val lemma = Dom.getAttribute(node, "lemma")!!.trim()
      var strongsNumbers = if (' ' in lemma) lemma.split("\\s+".toRegex()) else listOf(lemma)
      var prefix = if (':' in strongsNumbers[0]) strongsNumbers[0].substring(0, strongsNumbers[0].indexOf(':') + 1) else null
      if (null != prefix) strongsNumbers = strongsNumbers.map { it.replace(prefix, "") }
      strongsNumbers = strongsNumbers.map { val x = m_StrongsCorrections[it]; if (null != x) { m_StrongsCorrectionsApplied = true; x } else it }
      if (null != prefix) strongsNumbers = strongsNumbers.map { prefix + it }
      val newVal = strongsNumbers.joinToString(" ")
      Dom.setAttribute(node, "lemma", newVal)
    }


    /**************************************************************************/
    private fun readStrongsCorrectionFile ()
    {
      StandardFileLocations.getInputStream(StandardFileLocations.getStrongsCorrectionsFilePath(), null)!!.bufferedReader()
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .forEach { val x = it.split("=>"); m_StrongsCorrections[x[0].trim()] = x[1].trim() }
    }


    /**************************************************************************/
    private val m_StrongsCorrections = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)

  }


  /****************************************************************************/
  private var m_StrongsCorrectionsApplied = false
















  // The stuff below was based on some data which DIB was working on at some
  // stage.  I'm not sure whether this is still a candidate for completion.
  // I don't want to get rid of it until I know one way or the other.
  /****************************************************************************/
  fun analyse ()
  {
    if (true)
    {
      loadDataFile(ConfigData["stepExternalDataPath_TaggingMatJhn"]!!, false)
      loadDataFile(ConfigData["stepExternalDataPath_TaggingActRev"]!!, false)
    }
    else
    {
      loadDataFile(ConfigData["stepExternalDataPath_TaggingGenDeu"]!!, false)
      loadDataFile(ConfigData["stepExternalDataPath_TaggingJosEst"]!!, false)
      loadDataFile(ConfigData["stepExternalDataPath_TaggingJobSng"]!!, false)
      loadDataFile(ConfigData["stepExternalDataPath_TaggingIsaMal"]!!, false)
    }


    /**************************************************************************/
    /* Does the same Strong's turn up in different places with different
       morphology? */

//    val mapCheckDuplicateStrongsWithDifferentMorphology: MutableMap<String, String> = mutableMapOf()
//    m_CurrentData.forEach { verseThunk ->
//      val strongsAndGrammar = verseThunk.strongsAndGrammar.split('\t')
//      strongsAndGrammar.forEach {
//        val x = it.split('=')
//        if (x[0] in mapCheckDuplicateStrongsWithDifferentMorphology && mapCheckDuplicateStrongsWithDifferentMorphology[x[0]] != x[1])
//          Dbg.d("${x[0]}: ${mapCheckDuplicateStrongsWithDifferentMorphology[x[0]]} and ${x[1]}")
//        else
//          mapCheckDuplicateStrongsWithDifferentMorphology[x[0]] = x[1]
//      }
//    }



    /**************************************************************************/
    /* Does the same Strong's turn up in more than once in the same verse,
       and if so, does it have different morphology. */

//    m_CurrentData.forEach { verseThunk ->
//      val strongsAndGrammar = verseThunk.strongsAndGrammar.substring(1).split('\t')
//      val map: MutableMap<String, String> = mutableMapOf()
//      strongsAndGrammar.forEach {
//        val x = it.split('=')
//        if (x[0] in map)
//          map[x[0]] = map[x[0]] + "," + x[1]
//        else
//          map[x[0]] = x[1]
//      }
//
//      map.forEach { (strongs, morphology) ->
//        val countOfEntries = morphology.length - morphology.replace(",", "").length + 1
//        if (countOfEntries > 1)
//        {
//          var msg = "In ${verseThunk.ref} $strongs occurs more than once."
//          val check = morphology.replace(morphology.split(",")[0], "").replace(",", "")
//          if (check.isNotEmpty())
//            msg += "  Different morphology: $morphology"
//          Dbg.d(msg)
//        }
//      }
//    }


    /**************************************************************************/
    /* Is the text ever split up into a phrase rather than a single word? */

    m_CurrentData.forEach { verseThunk ->
      val bits = verseThunk.words.split('\t')
      bits.filter { ' ' in it.trim() }.forEach {
        Dbg.d("Phrase $it in ${verseThunk.ref}.")
      }
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Discussion                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /*
     The tagging data is ordered by scripture reference.  Originally I had in
     mind an approach to processing whereby we would step through the target
     data one verse at a time, each time stepping forward by one verse in the
     tagging data, and the two would automatically remain in step.  This would
     have meant there was no need to read either file into memory in its
     entirety, and would also have had the benefit of being (relatively)
     straightforward.

     Since then, however, it has become apparent that this simple-minded
     approach won't work.  In Biblica's deuHFA module, for instance, the
     translators have deliberately decided to put a few verses out of order.
     And versification is also an issue.  The tagging data is organised
     according to NRSV (???).  There is no guarantee that the text with which
     we are working is similarly organised, so we need to map between the
     structure of the tagging data and the structure of the text which we are
     tagging.
   */

  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                    Private -- applying tagging                         **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun processUsxBasedUponExistingStrongs (doc: Document)
  {
  }


  /****************************************************************************/
  private fun processUsxBasedUponVocab (doc: Document)
  {
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* See describeDataFiles. */

  private data class DataFileDescriptor (val m_BookNoLow: Int, val m_BookNoHigh: Int, val m_filePath: String)
  private val m_DataFileSelector: MutableList<DataFileDescriptor> = mutableListOf()



  /****************************************************************************/
  private data class VerseThunk (val ref: String, val words: String, val strongsAndGrammar: String)
  private val m_CurrentData: MutableList<VerseThunk> = mutableListOf()
  private var m_CurrentlyLoadedFilePath = ""
  private lateinit var m_ReversificationRenumbers: Map<RefKey, RefKey>



  /****************************************************************************/
  /* Not sure we're going to want this. */

  private data class WordDescriptor (val ix: Int, val strongs: String, val morphology: String)
  private val m_StrongsToDescriptor: MutableMap<String, WordDescriptor> = mutableMapOf()
  private val m_WordToDescriptor: MutableMap<String, WordDescriptor> = mutableMapOf()




  /****************************************************************************/
  private fun clearDataStructures ()
  {
    m_CurrentData.clear()
  }


  /****************************************************************************/
  /* The tagging files are rather large, on which basis I'm inclined to do
     something more complicated than simply load them all at one go.  This
     method sets up a list of descriptors, each indicating the low and high
     book no handled by the given file. */

  private fun describeDataFiles ()
  {
    fun describeDataFile (filePath: String)
    {
      val fromBookNo = BibleBookNamesUsx.nameToNumber(filePath.substring(filePath.length - 6).substring(0, 3))
      val toBookNo = BibleBookNamesUsx.nameToNumber(filePath.substring(filePath.length - 3))
      m_DataFileSelector.add(DataFileDescriptor(fromBookNo, toBookNo, filePath))
    }

    m_DataFileSelector.clear()
    ConfigData["stepExternalDataPath_TaggingAll"]!!.split(",").forEach { describeDataFile(it) }
  }


  /****************************************************************************/
  /* The reversification mappings are available from the ReversificationData
     class.  The only snag is that they're the wrong way round -- the mapping
     goes from source to NRSV, whereas we need NRSV to source.

     $$$ We have a significant loose end here, because the reversification data
     requires us to know the structure of the input text, the number of words
     in verses, etc.

     We do know this for USX, because the reversification processing was
     originally written to run with USX.  We don't know it for OSIS because
     we've never had to consider OSIS as a source -- previously by the time
     we've reached OSIS, we've already done any reversifiction processing.

     Now, however, we're saying we may have to deal with texts where the
     only source available to us is OSIS. */

  private fun getReversificationMappings ()
  {
    val reversificationMappings = ReversificationData.getReferenceMappings()
    m_ReversificationRenumbers = reversificationMappings.entries.associate { (key, value) -> value to key }
  }


  /****************************************************************************/
  /* Loads a single chunk of data.  This will be of the form:

      # Act.1.4	καὶ 	συναλιζόμενος 	παρήγγειλεν 	αὐτοῖς 	ἀπὸ 	Ἱεροσολύμων 	μὴ 	χωρίζεσθαι, 	ἀλλὰ 	περιμένειν
      #_Translation	And	being assembled together	He instructed	to them	from	Jerusalem	not	to depart,	but	to await
      #_Word=Grammar	G2532=CONJ	G4871=V-PNP-NSM	G3853=V-AAI-3S	G0846=P-DPM	G0575=PREP	G2414=N-GPN-L	G3361=PRT-N	G5563=V-PPN	G0235=CONJ	G4037=V-PAN
      #_Significant variant
      #_Act.1.4	τὴν 	ἐπαγγελίαν 	τοῦ 	πατρὸς 	ἣν 	ἠκούσατέ 	μου·
      #_Translation	the	promise	of the	Father	That which	you heard	of Me;
      #_Word=Grammar	G3588=T-ASF	G1860=N-ASF	G3588=T-GSM	G3962=N-GSM-T	G3739=R-ASF	G0191=V-AAI-2P	G1473=P-1GS
      #_Significant variant

     where the separators on each row are tabs, and there may or may not be
     one or more non-empty significant variant entries. */

  private fun loadChunk (chunk: List<String>)
  {
    val x = chunk.filterNot { "#_Significant variant" == it }
    var ix = 0
    while (ix < x.size)
    {
      loadSubchunk(x.subList(ix, ix + 3))
      ix += 3
      break // Pro tem we ignore variant information.
    }
  }


  /****************************************************************************/
  /* Loads a data file.  Files are large and complex, but I _think_ the only
     rows we are interested in start with a #, and come in the form of a
     number of adjacent rows of this kind, separated by one or more blank
     lines. */

  private fun loadDataFile (filePath: String, replaceExisting: Boolean)
  {
    if (filePath == m_CurrentlyLoadedFilePath) return
    m_CurrentlyLoadedFilePath = filePath

    if (replaceExisting)
      clearDataStructures()

    if (!filePath.startsWith("http")) Logger.warning("Running with local copy of tagging data $filePath.")
    val data = (if (filePath.startsWith("http")) URL(filePath).readText() else File(filePath).readText()).split("\n")

    var chunk: MutableList<String>? = null
    data.map { it.trim() } .filter { it.isEmpty() || it.startsWith("#")} .forEach {
      if (it.isEmpty())
      {
        if (null != chunk) loadChunk(chunk!!)
        chunk = null
      }
      else
      {
        if (null == chunk) chunk = mutableListOf()
        chunk!!.add(it)
      }
    }

    if (null != chunk)
      loadChunk(chunk!!)
  }


  /****************************************************************************/
  private fun loadDataFileForBook (usxBookName: String)
  {
    val bookNo = BibleBookNamesUsx.nameToNumber(usxBookName)
    val filePath = m_DataFileSelector.find { it.m_BookNoLow <= bookNo && bookNo <= it.m_BookNoHigh }!!.m_filePath
    loadDataFile(ConfigData[filePath]!!, true)
  }


  /****************************************************************************/
  /* Loads a single sub chunk of data.  This will be of the form:

      # Act.1.4	καὶ 	συναλιζόμενος 	παρήγγειλεν 	αὐτοῖς 	ἀπὸ 	Ἱεροσολύμων 	μὴ 	χωρίζεσθαι, 	ἀλλὰ 	περιμένειν
      #_Translation	And	being assembled together	He instructed	to them	from	Jerusalem	not	to depart,	but	to await
      #_Word=Grammar	G2532=CONJ	G4871=V-PNP-NSM	G3853=V-AAI-3S	G0846=P-DPM	G0575=PREP	G2414=N-GPN-L	G3361=PRT-N	G5563=V-PPN	G0235=CONJ	G4037=V-PAN

     where the separators on each row are tabs, and there may or may not be
     one or more non-empty significant variant entries.

     The first line starts '# ' on the primary definition for a given verse,
     and '#_' on variants. */

  private fun loadSubchunk (chunk: List<String>)
  {
    /**************************************************************************/
    val ref = parseRefKey(chunk[0])
    val words = chunk[0].substring(chunk[0].indexOf('\t') + 1)
    val strongsAndGrammar = chunk[2].substring(chunk[1].indexOf('\t') + 1)
    m_CurrentData.add(VerseThunk(ref, words, strongsAndGrammar))





//    /**************************************************************************/
//    var ix = 0
//
//
//
//    /**************************************************************************/
//    fun makeWordDescriptor (s: String) : WordDescriptor
//    {
//      val x = s.split("=")
//      return WordDescriptor(ix++, x[0], x[1])
//    }
//
//
//
//    /**************************************************************************/
//    val refAsString = parseRefKey(chunk[0])
//
//    var x = chunk[0].substring(chunk[0].indexOf('\t') + 1)
//    val words = x.split("\t")
//
//    x = chunk[2].substring(chunk[2].indexOf(' ') + 1)
//    ix = 0
//    val wordDescriptors = x.split("\t").map { makeWordDescriptor(it) }
//
//    wordDescriptors.forEach { m_StrongsToDescriptor[makeKey(refAsString, it.strongs)] = it }
  }


  /****************************************************************************/
  private fun makeKey (refAsString: String, otherBit: String): String
  {
    return refAsString + "_" + otherBit
  }


  /****************************************************************************/
  /* Parses the first row of a chunk to obtain the reference (in USX form). */

  private fun parseRefKey (s: String): String
  {
    var x = s.substring(2)
    x = x.substring(0, x.indexOf('\t'))
    x = x.replaceFirst('.', ' ')
    x = x.replaceFirst('.', ':')
    return x
  }
}