/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.stepexception.StepException
import java.util.*

/******************************************************************************/
/**
 * Obtains details of all of the versification schemes supported by osis2mod.
 *
 * @author ARA "Jamie" Jamieson
**/

/******************************************************************************/


object BibleStructuresSupportedByOsis2modAll
{
  /****************************************************************************/
  /**
   * osis2mod is sensitive to the case of the scheme name passed to it, so this
   * method gives back the canonical form.
   *
   * @param schemeName
   * @return Canonical form of scheme name.
  */

  fun canonicaliseSchemeName (schemeName: String): String
  {
    return m_CanonicalSchemeNameMappings[schemeName] ?: throw StepException("Invalid versification scheme: $schemeName")
  }


  /****************************************************************************/
  /**
  * Returns a sorted list of all of the schemes supported by osis2mod (or at
  * lest, those we're prepared to countenance using).
  *
  * @return List of schemes.
  */

  fun getSchemes () : List<String>
  {
    return m_BibleStructures.keys.sorted()
  }


  /****************************************************************************/
  /**
  * Gets the Bible structure for a particular versification scheme.
  *
  * @param versificationScheme What it says on the tin.  Must be one of the
  *   ones supported by osis2mod (all of which I load here), and not one
  *   of the ones I have been asked to ignore.
  */

  fun getStructureFor (versificationScheme: String): BibleStructureSupportedByOsis2modIndividual
  {
    return m_BibleStructures[versificationScheme.lowercase()]!!
  }


  /****************************************************************************/
  private fun addChapterDetails (theScheme: String, bookAbbreviation: String, versesInChapters: Array<Int>)
  {
    val scheme = theScheme.lowercase()
    if (C_UnwantedSchemes.contains(".$scheme.")) return
    var bibleStructure = m_BibleStructures[scheme]
    if (null == bibleStructure) { bibleStructure = BibleStructureSupportedByOsis2modIndividual(scheme); m_BibleStructures[scheme] = bibleStructure }
    bibleStructure.addChapterDetails(scheme, BibleBookNamesUsx.abbreviatedNameToNumber(bookAbbreviation), versesInChapters)
  }



  /****************************************************************************/
  private const val C_UnwantedSchemes = ".calvin.darbyfr."
  val m_BibleStructures: MutableMap<String, BibleStructureSupportedByOsis2modIndividual> = HashMap() // Mustn't be private, because I want NRSVA to be able to get at it.
  val m_CanonicalSchemeNameMappings: MutableMap<String, String> = TreeMap(String.CASE_INSENSITIVE_ORDER)



  /****************************************************************************/
  private fun initialiseBibleStructure ()
  {
    val lines = StandardFileLocations.getInputStream(StandardFileLocations.getOsis2modVersificationDetailsFilePath(), null)!!.bufferedReader().use { it.readText() } .lines()
    lines.forEach {
      var line = it.trim()
      if (!line.startsWith("#") && line.isNotEmpty())
      {
        var (scheme, bookName, chapterLengths) = line.split("/")
        addChapterDetails(scheme, bookName, chapterLengths.split("\\s*,\\s*".toRegex()).map { it.toInt() } .toTypedArray())
      }
    }
  }


  /****************************************************************************/
  private fun initialiseCanonicalSchemeNameMapping ()
  {
    m_CanonicalSchemeNameMappings["Calvin"] = "Calvin"
    m_CanonicalSchemeNameMappings["Catholic"] = "Catholic"
    m_CanonicalSchemeNameMappings["Catholic2"] = "Catholic2"
    m_CanonicalSchemeNameMappings["DarbyFr"] = "DarbyFr"
    m_CanonicalSchemeNameMappings["German"] = "German"
    m_CanonicalSchemeNameMappings["KJV"] = "KJV"
    m_CanonicalSchemeNameMappings["KJVA"] = "KJVA"
    m_CanonicalSchemeNameMappings["LXX"] = "LXX"
    m_CanonicalSchemeNameMappings["Leningrad"] = "Leningrad"
    m_CanonicalSchemeNameMappings["Luther"] = "Luther"
    m_CanonicalSchemeNameMappings["MT"] = "MT"
    m_CanonicalSchemeNameMappings["NRSV"] = "NRSV"
    m_CanonicalSchemeNameMappings["NRSVA"] = "NRSVA"
    m_CanonicalSchemeNameMappings["Orthodox"] = "Orthodox"
    m_CanonicalSchemeNameMappings["Segond"] = "Segond"
    m_CanonicalSchemeNameMappings["Synodal"] = "Synodal"
    m_CanonicalSchemeNameMappings["SynodalProt"] = "SynodalProt"
    m_CanonicalSchemeNameMappings["Vulg"] = "Vulg"  }


  /****************************************************************************/
  /* A line in the data looks like    Calvin/Rut/22, 23, 18, 22   */

  init
  {
    initialiseBibleStructure()
    initialiseCanonicalSchemeNameMapping()
  }
 }