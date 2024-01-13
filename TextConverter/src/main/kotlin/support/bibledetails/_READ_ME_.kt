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
* - **[TextStructure]** relates files to Bible books.  So if you need
*   to iterate over files such that you are processing books in order, or if you
*   need to know which file corresponds to a particular book, this is the place
*   to look.  There are various derived classes which are tied to particular
*   folders -- eg InputUsx.
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
*   various books, the number of verses in the various chapters, etc.  In
*   general you won't acccess this directly -- each variant of TextStructure
*   has its own associated BibleStructure instance, and you can access it via
*   the TextStructure instance.
*
* @author ARA 'Jamie' Jamieson
*/

private class _READ_ME_