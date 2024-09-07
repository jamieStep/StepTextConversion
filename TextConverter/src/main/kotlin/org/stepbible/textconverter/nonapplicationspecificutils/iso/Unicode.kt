package org.stepbible.textconverter.nonapplicationspecificutils.iso

/******************************************************************************/
/**
 * Supplies information about Unicode characters.
 *
 * @author ARA "Jamie" Jamieson
 */

object Unicode
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
  * Given a string containing characters taken from the canonical portion of
  * the text, determines from the first such character whether the script is
  * LTR or RTL.
  *
  * @param s String whose first character is examined.
  * @return LTR or RTL
  */

  fun getTextDirection (s: String): String
  {
    if (s.isEmpty()) return "LTR"
    return if (textDirectionIsRtl(s[0].toChar().code)) "RTL" else "LTR"
  }


  /****************************************************************************/
  /* Does what it says on the tin.  Code taken from
     https://stackoverflow.com/questions/4330951/how-to-detect-whether-a-character-belongs-to-a-right-to-left-language
 */

  private fun textDirectionIsRtl (c: Int): Boolean
  {
    if (c !in 0x5BE..0x10B7F)       return false

    if (c <= 0x85E)
    {
      if (c == 0x5BE)                     return true
      if (c == 0x5C0)                     return true
      if (c == 0x5C3)                     return true
      if (c == 0x5C6)                     return true
      if (c in 0x5D0 .. 0x5EA)      return true
      if (c in 0x5F0 .. 0x5F4)      return true
      if (c == 0x608)                     return true
      if (c == 0x60B)                     return true
      if (c == 0x60D)                     return true
      if (c == 0x61B)                     return true
      if (c in 0x61E .. 0x64A)      return true
      if (c in 0x66D .. 0x66F)      return true
      if (c in 0x671 .. 0x6D5)      return true
      if (c in 0x6E5 .. 0x6E6)      return true
      if (c in 0x6EE .. 0x6EF)      return true
      if (c in 0x6FA .. 0x70D)      return true
      if (c == 0x710)                     return true
      if (c in 0x712 .. 0x72F)      return true
      if (c in 0x74D .. 0x7A5)      return true
      if (c == 0x7B1)                     return true
      if (c in 0x7C0 .. 0x7EA)      return true
      if (c in 0x7F4 .. 0x7F5)      return true
      if (c == 0x7FA)                     return true
      if (c in 0x800 .. 0x815)      return true
      if (c == 0x81A)                     return true
      if (c == 0x824)                     return true
      if (c == 0x828)                     return true
      if (c in 0x830 .. 0x83E)      return true
      if (c in 0x840 .. 0x858)      return true
      if (c == 0x85E)                     return true
    }

    if (c == 0x200F)                      return true

    if (c >= 0xFB1D)
    {
      if (c == 0xFB1D)                    return true
      if (c in 0xFB1F .. 0xFB28)    return true
      if (c in 0xFB2A .. 0xFB36)    return true
      if (c in 0xFB38 .. 0xFB3C)    return true
      if (c == 0xFB3E)                    return true
      if (c in 0xFB40 .. 0xFB41)    return true
      if (c in 0xFB43 .. 0xFB44)    return true
      if (c in 0xFB46 .. 0xFBC1)    return true
      if (c in 0xFBD3 .. 0xFD3D)    return true
      if (c in 0xFD50 .. 0xFD8F)    return true
      if (c in 0xFD92 .. 0xFDC7)    return true
      if (c in 0xFDF0 .. 0xFDFC)    return true
      if (c in 0xFE70 .. 0xFE74)    return true
      if (c in 0xFE76 .. 0xFEFC)    return true
      if (c in 0x10800 .. 0x10805)  return true
      if (c == 0x10808)                   return true
      if (c in 0x1080A .. 0x10835)  return true
      if (c in 0x10837 .. 0x10838)  return true
      if (c == 0x1083C)                   return true
      if (c in 0x1083F .. 0x10855)  return true
      if (c in 0x10857 .. 0x1085F)  return true
      if (c in 0x10900 .. 0x1091B)  return true
      if (c in 0x10920 .. 0x10939)  return true
      if (c == 0x1093F)                   return true
      if (c == 0x10A00)                   return true
      if (c in 0x10A10 .. 0x10A13)  return true
      if (c in 0x10A15 .. 0x10A17)  return true
      if (c in 0x10A19 .. 0x10A33)  return true
      if (c in 0x10A40 .. 0x10A47)  return true
      if (c in 0x10A50 .. 0x10A58)  return true
      if (c in 0x10A60 .. 0x10A7F)  return true
      if (c in 0x10B00 .. 0x10B35)  return true
      if (c in 0x10B40 .. 0x10B55)  return true
      if (c in 0x10B58 .. 0x10B72)  return true
      if (c in 0x10B78 .. 0x10B7F)  return true
    }

    return false
  }
}