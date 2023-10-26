package org.stepbible.textconverter.support.bibledetails

/******************************************************************************/
/**
* Library for Bible-related information.
*
* - **[BibleAnatomy]** tells you things about Bibles in general -- whether a
*   given book is in the OT, which are single-chapter books, which verses Bibles
*   commonly omit (and therefore do not need to be reported(), etc.  The
*   information here is knowable without reference to any particular Bible, and
*   therefore is available prior to any processing.
*
* - **[BibleBookAndFileMapper]** relates files to Bible books.  So if you need
*   to iterate over files such that you are processing books in order, or if you
*   need to know which file corresponds to a particular book, this is the place
*   to look.  There are two derived classes, one of which looks at the RawUsx
*   folder and the other at the EnhancedUsx folder.
*
* - **[BibleBookNames]** does just what the name suggests -- it supplies the
*   names used for the various book (abbreviated, short and long, as available).
*   There are three derived classes, one for USX (UBS), one for OSIS, and one
*   for the vernacular text being processed.  The first two are fixed in nature.
*   The last is derived from the metadata.  Between them, these make it possible
*   to take a given book number and deduce the relevant names.
*
* - **[BibleStructure]** differs from BibleAnatomy in that it contains
*   information tied to a particular text -- the number of chapters in the
*   various books, the number of verses in the various chapters, etc.  There's a
*   slightly complicated inheritance structure here.  The thing you are likely
*   to want to use most often is **[BibleStructure.UsxUnderConstructionInstance()]**.
*   And the only other thing likely to be of interest is
*   **[BibleStructureNrsvx]**, which gives you details either of NRSV or of
*   NRSVA, depending upon whether you are working on a text with DC books or
*   not.  The other classes are used only pursuant to the task of working
*   out which is the best among the various versification schemes supported
*   by osis2mod.
*
* @author ARA 'Jamie' Jamieson
*/

private class _READ_ME_