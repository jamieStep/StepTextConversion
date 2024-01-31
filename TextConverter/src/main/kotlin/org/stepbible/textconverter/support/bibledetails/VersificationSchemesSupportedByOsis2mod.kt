/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.stepexception.StepException
import java.util.*

/******************************************************************************/
/**
 * Obtains details of all of the versification schemes supported by osis2mod.
 *
 * @author ARA "Jamie" Jamieson
**/

object VersificationSchemesSupportedByOsis2mod
{
  /****************************************************************************/
  /**
   * osis2mod is sensitive to the case of the scheme name passed to it, so this
   * method gives back the canonical form.  Names starting v11n are takig
   *
   * @param schemeName
   * @return Canonical form of scheme name.
  */

  fun canonicaliseSchemeName (schemeName: String): String
  {
    return if ("tbd" == schemeName) schemeName else m_CanonicalSchemeNameMappings[schemeName] ?: throw StepException("Invalid versification scheme: $schemeName")
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
    return m_CanonicalSchemeNameMappings.keys.sorted()
  }


  /****************************************************************************/
  private val m_CanonicalSchemeNameMappings: MutableMap<String, String> = TreeMap(String.CASE_INSENSITIVE_ORDER) // This maps canonical name to canonical name, but because it is not case-sensitive, in fact the key can be in any case.



  /****************************************************************************/
  /* A line in the data looks like    Calvin/Rut/22, 23, 18, 22   */

  init
  {
    val C_UnwantedSchemes = ".calvin.darbyfr."
    FileLocations.getInputStream(FileLocations.getOsis2modVersificationDetailsFilePath(), null)!!.bufferedReader().use { it.readText() } .lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith('#') } // Remove comments and blanks
      .map { it.substring(0, it.indexOf('/')) }          // Get the scheme name
      .filter { it !in C_UnwantedSchemes }                    // Exclude unwanted schemes.
      .forEach { m_CanonicalSchemeNameMappings[it] = it }
  }
 }