package org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import java.text.NumberFormat
import java.util.*

/****************************************************************************/
/**
 * Locale information appropriate to the vernacular text.
 *
 * @author ARA "Jamie" Jamieson
 */

object LocaleHandler
{
  /****************************************************************************/
  /**
  * Returns the locale appropriate to the vernacular text.
  *
  * @return Locale.
  */

  fun getVernacularLocale (): Locale
  {
    initialiseIfNecessary()
    return m_Locale!!
  }


  /****************************************************************************/
  /**
  * Returns a NumberFormat object appropriate to the vernacular text.
  *
  * @ Return NumberFormat.
  */

  fun getVernacularNumberFormat (): NumberFormat
  {
    initialiseIfNecessary()
    return NumberFormat.getInstance(m_Locale)
  }


  /****************************************************************************/
  private fun initialiseIfNecessary ()
  {
    if (null != m_Locale) return

    val languageCode = ConfigData["stepLanguageCode2Char"]
    val languageCountryCode = ConfigData["stepSuppliedCountryCode"]
    val languageVariant = ConfigData["stepSuppliedScriptCode"]

    m_Locale =
      if (null != languageCode && null != languageCountryCode && null != languageVariant)
        Locale(languageCode, languageCountryCode, languageVariant)
      else if (null != languageCode && null != languageCountryCode)
        Locale(languageCode, languageCountryCode)
      else if (null != languageCode)
        Locale(languageCode)
      else
        Locale.getDefault()
  }

  private var m_Locale: Locale? = null
}