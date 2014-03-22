Overview
========
* A Shelf is a data system consisting of Boxes, which is self-contained. That is, it should not contain references to external data, data in other Shelves, etc. The concept of the Shelf allows for different implementations of the data system, and/or completely independent data sets for efficiency where no cross-references are needed.
* All data is stored in Boxes in the Shelf.
* All data is versioned, according to Revision. These are numbered from 0, and may sometimes be denoted as R0, R1 etc.
* Each box exists for some contiguous range of Revisions. It has no meaning or existence outside this range.
* Each box contains one value in each Revision where it exists.
* All data in a Shelf is only mutable in the sense that Boxes may contain different values in different revisions. As a result, Boxes values are always either strictly immutable data (for example AnyVals, Strings, immutable Lists, immutable Maps and other collections, case classes) or immutable data containing other Boxes, for example we might have a case class that contains several Boxes as fields, and hence functions similarly to a Java Bean.
* As a result of the above, the value of a Box at a given Revision will never change in any way.

The following table shows some of these features, as three boxes A, B and C are altered in successive revisions:


Rev. | A | B | C | Comment
---- | - | - | - | -------
0    | 0 | X | X | Boxes may not exist in some revisions
1    | ... | 0 | X | Boxes are always created with an initial value
2    | 1 | 1 | 0 | Multiple boxes can change in one revision
3    | ... | ... | 1 | For most revisions, most boxes won't change
4    | 2 | ... | ... | The state of a box in a revision may be taken from a previous revision, here B has not changed since R2, and C since R3.
5    | X | ... | ... | A Box may be deleted in a revision
6    | X | ... | ... | And it cannot come back. Note that here we use A, B and C to refer to Boxes, however internally this would be an id that is never reused.


Key:

Symbol | Meaning
------ | -------
X | Box does not exist
... | Box does not change in this revision
N | Box has value N in this revision

Transactions
============
To allow for efficient concurrent operation, we wish to support concurrent transactions.
Each transaction will:

1. **Start.** The transaction receives an immutable copy of the Shelf state (all Boxes) at the start revision RS.
2. **Run.** The transaction reads data from boxes at RS, and so sees a consistent state of the Shelf. A read log is kept of all Boxes that are read. When boxes are updated (their value changed) the changes are added to a write log. If updated boxes are read, the most recent value from the write log is seen.
3. **Commit.** The transaction completes, and an attempt is made to commit the changes in the log. If no changes have been made to any Boxes in the read or write log since RS, the transaction is committed, creating a new revision where all changes in the write log are made atomically. If changes have been made, the read and write logs are discarded and the transaction restarts from stage 1 with a new start revision.

Implementation
==============
Each revision can be represented by an immutable map from boxes to values. Since values are immutable the entire data structure is immutable, providing a "free" snapshot of a revision by just retaining the map for that revision.
In order not to use increasing memory allocation as changes occur, we must discard old revisions - this is easily implemented since transactions will retain the map for the revision they use until they commit, at which point it should become available for GC. Optionally, revisions may be retained longer to allow for a view of data history, for example for "undo" functionality.
In order to operate reasonably efficiently, the immutable map must be a persistent data structure, luckily this is provided by Scala. In general, persistent data structures work well, since we will often for example update a Box containing a List by adding elements, which is efficient when a persistent List is used.
The sparse nature of Box changes is well suited to a peristent data structure, since we expect only to have to rebuild a small portion of the map data structure when only one Box changes, and other portions of the data structure will be shared.