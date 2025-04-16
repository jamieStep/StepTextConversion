/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.bibledetails

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import java.util.*

/******************************************************************************/
/**
 * Obtains details of all of the versification schemes supported by osis2mod.
 *
 * @author ARA "Jamie" Jamieson
**/

object VersificationSchemesSupportedByOsis2mod: ObjectInterface
{
  /****************************************************************************/
  /**
   * osis2mod is sensitive to the case of the scheme name passed to it, so this
   * method gives back the canonical form.  'tbd' indicates a situation in
   * which we are creating our own details, and the tbd is resolved elsewhere.
   *
   * @param schemeName
   * @return Canonical form of scheme name.
  */

  fun canonicaliseSchemeName (schemeName: String): String
  {
    return if ("tbd" == schemeName) schemeName else m_CanonicalSchemeNameMappings[schemeName] ?: throw StepExceptionWithStackTraceAbandonRun("Invalid versification scheme: $schemeName")
  }


  /****************************************************************************/
  /**
  * Returns a sorted list of all of the schemes supported by osis2mod (or at
  * least, those we're prepared to countenance using).
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
  /* A line in the data looks like    Calvin/Rut/22, 23, 18, 22   where '/'
     represents a tab. */

  init { doInit() }

  @Synchronized private fun doInit ()
  {
    val C_UnwantedSchemes = ".calvin.darbyfr."
    StepFileUtils.readDelimitedTextStream(FileLocations.getInputStream(FileLocations.getOsis2modVersificationDetailsFilePath()).first!!)
      .filter { it[0].lowercase() !in C_UnwantedSchemes }
      .forEach { m_CanonicalSchemeNameMappings[it[0]] = it[0] }
  }
}