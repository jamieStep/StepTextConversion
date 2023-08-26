/******************************************************************************/
/*               Extracts details from the Sword header files.                */
/******************************************************************************/

#define SWORD_NAMESPACE_START
#define SWORD_NAMESPACE_END

struct sbook {
	/**Name of book
	*/
	const char *name;

	/**OSIS name
	*/
	const char *osis;

	/**Preferred Abbreviation
	*/
	const char *prefAbbrev;

	/**Maximum chapters in book
	*/
	unsigned char chapmax;
	/** Array[chapmax] of maximum verses in chapters
	*/
	//int *versemax;
};

struct abbrev
{
	const char *ab;
	const char *osis;
};

#define NENTRIES(x) (sizeof(x) / sizeof(x[0]))

#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_calvin.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_catholic.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_catholic2.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_darbyfr.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_german.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_kjva.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_leningrad.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_luther.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_lxx.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_mt.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_nrsva.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_nrsv.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_null.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_orthodox.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_segond.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_synodal.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_synodalprot.h"
#include "C:\Users\Jamie\Desktop\sword-1.8.1\include\canon_vulg.h"

#include <stdio.h>

int main()
{
  int verseIx;
  
  verseIx = 0; verseIx = extract(otbooks, NENTRIES(otbooks), vm_calvin, "calvin", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_calvin, "calvin", verseIx);
	       
  verseIx = 0; verseIx = extract(otbooks_catholic, NENTRIES(otbooks_catholic), vm_catholic, "catholic", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_catholic, "catholic", verseIx);

  verseIx = 0; verseIx = extract(otbooks_catholic2, NENTRIES(otbooks_catholic2), vm_catholic2, "catholic2", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_catholic2, "catholic2", verseIx);

  verseIx = 0; verseIx = extract(otbooks, NENTRIES(otbooks), vm_darbyfr, "darbyfr", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_darbyfr, "darbyfr", verseIx);
	       
  verseIx = 0; verseIx = extract(otbooks_german, NENTRIES(otbooks_german), vm_german, "german", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_german, "german", verseIx);

  verseIx = 0; verseIx = extract(otbooks_kjva, NENTRIES(otbooks_kjva), vm_kjva, "kjva", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_kjva, "kjva", verseIx);

  // I assume KJV is just the common structure -- there's no header file for it, but osis2mod supports it.
  verseIx = 0; verseIx = extract(otbooks, NENTRIES(otbooks), vm, "kjv", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm, "kjv", verseIx);

  verseIx = 0; verseIx = extract(otbooks_leningrad, NENTRIES(otbooks_leningrad), vm_leningrad, "leningrad", verseIx);

  verseIx = 0; verseIx = extract(otbooks_luther, NENTRIES(otbooks_luther), vm_luther, "luther", verseIx);
               verseIx = extract(ntbooks_luther, NENTRIES(ntbooks_luther), vm_luther, "luther", verseIx);

  verseIx = 0; verseIx = extract(otbooks_lxx, NENTRIES(otbooks_lxx), vm_lxx, "lxx", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_lxx, "lxx", verseIx);

  verseIx = 0; verseIx = extract(otbooks_mt, NENTRIES(otbooks_mt), vm_mt, "mt", verseIx);

  verseIx = 0; verseIx = extract(otbooks, NENTRIES(otbooks), vm_nrsv, "nrsv", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_nrsv, "nrsv", verseIx);

  verseIx = 0; verseIx = extract(otbooks_nrsva, NENTRIES(otbooks_nrsva), vm_nrsva, "nrsva", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_nrsva, "", verseIx);

  verseIx = 0; verseIx = extract(otbooks_orthodox, NENTRIES(otbooks_orthodox), vm_orthodox, "orthodox", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_orthodox, "orthodox", verseIx);

  verseIx = 0; verseIx = extract(otbooks, NENTRIES(otbooks), vm_segond, "segond", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm_segond, "segond", verseIx);

  verseIx = 0; verseIx = extract(otbooks_synodal, NENTRIES(otbooks_synodal), vm_synodal, "synodal", verseIx);
               verseIx = extract(ntbooks_synodal, NENTRIES(ntbooks_synodal), vm_synodal, "synodal", verseIx);

  verseIx = 0; verseIx = extract(otbooks_synodalProt, NENTRIES(otbooks_synodalProt), vm_synodalProt, "synodalProt", verseIx);
               verseIx = extract(ntbooks_synodal, NENTRIES(ntbooks_synodal), vm_synodalProt, "synodalProt", verseIx);

  verseIx = 0; verseIx = extract(otbooks_vulg, NENTRIES(otbooks_vulg), vm_vulg, "vulg", verseIx); printf("%d\n", NENTRIES(otbooks_vulg));
               verseIx = extract(ntbooks_vulg, NENTRIES(ntbooks_vulg), vm_vulg, "vulg", verseIx);
  
  verseIx = 0; verseIx = extract(otbooks, NENTRIES(otbooks), vm, "COMMON", verseIx);
               verseIx = extract(ntbooks, NENTRIES(ntbooks), vm, "COMMON", verseIx);
  
  return 0;
}


int extract (struct sbook books[], int nBooks, int verses[], char *schemeName, int verseIx)
{
  int bookNo;
  for (bookNo = 0; bookNo < nBooks; ++bookNo)
  {
    if (0 != books[bookNo].chapmax)
    {
      printf("%s\t%s\t%s\t%s\t\%d\tBook\n", schemeName, books[bookNo].prefAbbrev, books[bookNo].name, books[bookNo].osis, books[bookNo].chapmax);
      int v;
      for (v = 0; v < books[bookNo].chapmax; ++v)
	printf("%s\t%s\t\%d\t%d\n", schemeName, books[bookNo].prefAbbrev, v + 1, verses[verseIx++]);
    }
  }

  
  return 0;
}
