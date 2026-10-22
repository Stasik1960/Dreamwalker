package dev.dreamwalker.bloodbornedw.composite;

/** The source_* factory uses this class; original prototype roots keep their
 * original class and original Guava transition tables. */
public final class ImportedRootBlock extends CompositeRootBlock implements NewStateStorage {
    public ImportedRootBlock(CompositeSpec spec){super(spec);}
}
