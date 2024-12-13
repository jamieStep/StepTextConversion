///******************************************************************************/
//package org.stepbible.textconverter.nonapplicationspecificutils.bibledetails
//
//import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
//import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
//
//
///******************************************************************************/
///**
// * Definitive list of book names and numbers, and the wherewithal to map
// * between them.
// *
// * The information here comes from the configuration information.  This class
// * should therefore not be used until the configuration data has been loaded.
// *
// * @author ARA "Jamie" Jamieson
//*/
//
//object BibleBookNamesTextAsSupplied: BibleBookNames(), ObjectInterface
//{
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                                Public                                  **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  /**
//  * Gets the abbreviated name for a given book number.
//  *
//  * @param bookNo
//  * @return Abbreviated name.
//  */
//
//  operator fun get (bookNo: Int): String = numberToAbbreviatedName(bookNo)
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                                Private                                 **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  init { doInit() }
//
//  @Synchronized private fun doInit ()
//  {
//    var bookDescriptors = ConfigData.getBookDescriptors()
//    if (bookDescriptors.isEmpty()) bookDescriptors = BibleBookNamesUsx.getBookDescriptors().toMutableList()
//
//    var previousBookNo = -1
//
//    for (elt in bookDescriptors)
//    {
//      /************************************************************************/
//      var bookNo: Int
//      val usxName = elt.ubsAbbreviation
//      var longName = elt.vernacularLong
//      var shortName = elt.vernacularShort
//      var abbreviatedName = elt.vernacularAbbreviation
//
//
//
//      /************************************************************************/
//      /* We do not place any particular requirement upon which elements are
//         supplied, so we need to cater for the possibility that some have _not_
//         been provided.  They all need to be filled in, so we'll have to copy
//         them around as necessary. */
//
//      if (abbreviatedName.isEmpty()) abbreviatedName = shortName
//      if (abbreviatedName.isEmpty()) abbreviatedName = longName
//
//      if (shortName.isEmpty()) shortName = longName
//      if (shortName.isEmpty()) shortName = abbreviatedName
//
//      if (longName.isEmpty()) longName = shortName
//      if (longName.isEmpty()) longName = abbreviatedName
//
//      //$$$if (abbreviatedName.isEmpty() || shortName.isEmpty() || longName.isEmpty()) throw StepException("Vernacular book names: no vernacular definition for $usxName")
//
//      if (usxName.isEmpty())
//        bookNo = ++previousBookNo
//      else
//      {
//        bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(usxName)
//        previousBookNo = bookNo
//      }
//
//      BibleBookNamesTextAsSupplied.addBookDescriptor(bookNo, abbreviatedName, shortName, longName)
//    }
//  }
//}
