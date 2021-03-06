package org.pharmgkb.pharmcat.reporter.model.result;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.pharmgkb.common.comparator.HaplotypeNameComparator;
import org.pharmgkb.pharmcat.reporter.model.VariantReport;


/**
 * Model class to represent a diplotype and all derived information
 *
 * @author Ryan Whaley
 */
public class Diplotype implements Comparable<Diplotype> {

  private static final String NA = "N/A";
  private static final String sf_toStringPattern = "%s:%s";
  private static final String sf_delimiter = "/";
  private static final String sf_homTemplate = "Two %s alleles";
  private static final String sf_hetTemplate = "One %s allele and one %s allele";
  private static final String sf_hetSuffix = " (heterozygous)";
  private static final String sf_homSuffix = " (homozygous)";

  private Haplotype m_allele1;
  private Haplotype m_allele2;
  private String m_gene;
  private String m_phenotype;
  private VariantReport m_variant;

  /**
   * This Function can be used in reduce() calls
   */
  public static final BinaryOperator<String> phasedReducer = (a,b)-> {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }

    int left = 0;
    int right = 1;

    String[] aHaps = a.split(sf_delimiter);
    String[] bHaps = b.split(sf_delimiter);

    Set<String> finalLeft = new TreeSet<>(HaplotypeNameComparator.getComparator());
    Set<String> finalRight = new TreeSet<>(HaplotypeNameComparator.getComparator());

    addHaps(finalLeft, aHaps[left]);
    addHaps(finalRight, aHaps[right]);

    if (finalLeft.contains(bHaps[right]) || finalRight.contains(bHaps[left])) {
      addHaps(finalLeft, bHaps[right]);
      addHaps(finalRight, bHaps[left]);
    }
    else {
      addHaps(finalLeft, bHaps[left]);
      addHaps(finalRight, bHaps[right]);
    }

    String joined0 = finalLeft.stream().collect(Collectors.joining("+"));
    String joined1 = finalRight.stream().collect(Collectors.joining("+"));

    return joined0+sf_delimiter+joined1;
  };

  /**
   * When multiple diplotype calls are phased they can be combined into one String in certain circumstances. This method
   * combines things like a/b and a/c into a/b+c.
   * @param dips a {@link Collection} of diplotype strings in the form "a/b", "a/c", etc... 
   * @return a single String combining the diplotypes, e.g. "a/b+c"
   */
  @Nonnull
  public static String reducePhasedDiplotypes(@Nullable Collection<String> dips) {
    if (dips == null || dips.size() == 0) return "";
    
    final Set<String> leftBucket = new TreeSet<>(HaplotypeNameComparator.getComparator());
    final Set<String> rightBucket = new TreeSet<>(HaplotypeNameComparator.getComparator());
    
    dips.forEach(d -> {
      String[] haps = d.split(sf_delimiter);
      if (!rightBucket.contains(haps[0])) {
        leftBucket.add(haps[0]);
        rightBucket.add(haps[1]);
      } else {
        leftBucket.add(haps[1]);
        rightBucket.add(haps[0]);
      }
    });
    
    Function<String,String> leftMapper = leftBucket.size() > 1 ? hapNameEncloser : Function.identity();
    String left = leftBucket.stream().map(leftMapper).collect(Collectors.joining("+"));
    Function<String,String> rightMapper = rightBucket.size() > 1 ? hapNameEncloser : Function.identity();
    String right = rightBucket.stream().map(rightMapper).collect(Collectors.joining("+"));
    
    return left + sf_delimiter + right;
  }

  /**
   * Takes diplotype names and encloses them in parentheses if they contain a "+"
   */
  private static final Function<String,String> hapNameEncloser = h -> {
    if (h.contains("+")) return "(" + h + ")"; else return h;
  };

  private static void addHaps(Set<String> hapSet, String hap) {
    hapSet.addAll(Arrays.asList(hap.split("\\+")));
  }

  /**
   * public constructor
   */
  public Diplotype(String gene, Haplotype h1, Haplotype h2) {

    m_allele1 = h1;
    m_allele2 = h2;
    m_gene = gene;
  }

  /**
   * Gets the gene this diplotype is for
   * @return a HGNC gene symbol
   */
  public String getGene() {
    return m_gene;
  }

  /**
   * Gets the first {@link Haplotype} listed in this diplotype
   */
  private Haplotype getAllele1() {
    return m_allele1;
  }

  /**
   * Gets the second {@link Haplotype} listed in this diplotype
   */
  private Haplotype getAllele2() {
    return m_allele2;
  }

  /**
   * Does this diplotype have an allele with the given name
   * @param alleleName an allele name, e.g. "*10"
   * @return true if this diplotype contains an allele with the given name
   */
  public boolean hasAllele(String alleleName) {
    return (m_allele1 != null && m_allele1.getName().equals(alleleName))
        || (m_allele2 != null && m_allele2.getName().equals(alleleName));
  }

  /**
   * Flag for whether this diplotype includes an incidental finding
   * @return true if this diplotype includes an incidental finding allele
   */
  public boolean isIncidental() {
    return m_allele1.isIncidental() || m_allele2.isIncidental();
  }

  /**
   * Gets a Sting representation of this haplotype with no gene prefix (e.g. *1/*10)
   */
  public String printBare() {

    Optional<String> override = printOverride();
    if (override.isPresent()) {
      return override.get();
    }

    String[] alleles = new String[]{m_allele1.printDisplay(), m_allele2.printDisplay()};
    Arrays.sort(alleles, HaplotypeNameComparator.getComparator());
    return Arrays.stream(alleles).collect(Collectors.joining(sf_delimiter));
  }

  /**
   * Gets a String representation of this Diplotype that can be used to display in output. This should <em>NOT</em> be
   * used for matching to annotation groups
   * @return a String display for this diplotype, without Gene symbol
   */
  public String printDisplay() {
    if (getVariant() != null) {
      return getVariant().printDisplay();
    }
    else {
      return printBare();
    }
  }

  /**
   * Gets a string diplotype pair used to look up matching guideline groups. This could be different than what's displayed in the
   * report to the user so we use a separate method.
   * @return a String key used to match guideline groups without gene symbol (e.g. *4/*10)
   */
  public String printBareLookupKey() {
    String[] alleles = new String[]{m_allele1.printLookup(), m_allele2.printLookup()};
    Arrays.sort(alleles, HaplotypeNameComparator.getComparator());
    return Arrays.stream(alleles).collect(Collectors.joining(sf_delimiter));
  }

  /**
   * Gets a string key used to look up matching guideline groups. This could be different than what's displayed in the
   * report to the user so we use a separate method.
   * @return a String key used to match guideline groups without gene symbol (e.g. *4/*10)
   */
  public String printLookupKey() {
    return m_gene + ":" + printBareLookupKey();
  }

  /**
   * Gets a String phrase describing the individual haplotype functions, e.g. "Two low function alleles"
   *
   * Will print a default N/A String if no functions exist
   */
  public String printFunctionPhrase() {

    String f1 = getAllele1() != null && getAllele1().getFunction() != null ?
        getAllele1().getFunction().toLowerCase() : null;
    String f2 = getAllele2() != null && getAllele2().getFunction() != null ?
        getAllele2().getFunction().toLowerCase() : null;

    if (StringUtils.isNotBlank(f1) && StringUtils.isNotBlank(f2)) {

      if (f1.equals(f2)) {
        return String.format(sf_homTemplate, f1);
      }
      else {
        return String.format(sf_hetTemplate, f1, f2);
      }

    }
    return GeneReport.NA;
  }

  /**
   * Gets a String representation of this diplotype with the gene as prefix, e.g. GENEX:*1/*2
   */
  public String toString() {
    return String.format(sf_toStringPattern, m_gene, printBare());
  }

  /**
   * Gets a String term for the overall phenotype of this Diplotype
   *
   * Will print a default N/A String if no phenotype exists
   */
  public String getPhenotype() {
    return m_phenotype == null ? NA : m_phenotype;
  }

  public void setPhenotype(String phenotype) {
    m_phenotype = phenotype;
  }

  /**
   * Print the overriding diplotype string if it exists
   * @return Optional diplotype string to override whatever the actual string would be
   */
  private Optional<String> printOverride() {
    boolean refAllele1 = getAllele1().isReference();
    boolean refAllele2 = getAllele2().isReference();

    switch (m_gene) {

      case "CFTR":
        if (getAllele1().isIncidental() && getAllele2().isIncidental()) {
          return Optional.of(getAllele1().getName() + sf_homSuffix);
        }

        if (refAllele1 && refAllele2) {
          return Optional.of("No CPIC variants found");
        }
        else if (refAllele1 || refAllele2) {
          String allele = refAllele1 ? getAllele2().getName() : getAllele1().getName();
          return Optional.of(allele + sf_hetSuffix);
        }
        break;

      case "DPYD":
        if (refAllele1 && refAllele2) {
          return Optional.of("No CPIC decreased or no function variant with strong or moderate evidence found");
        }
        break;
    }
    return Optional.empty();
  }

  /**
   * Makes a {@link Stream} of zygosity descriptors for this diplotype, e.g. *60 (heterozygous). This is a stream since
   * a single Diplotype can be described in 0, 1, or 2 Strings, depending on the particular allele.
   * @return a Stream of 0 or more zygosity Strings
   */
  public Stream<String> streamAllelesByZygosity() {
    if (getAllele1().equals(getAllele2())) {
      if (getAllele1().isReference()) {
        return Stream.empty();
      }
      return Stream.of(getAllele1().printDisplay() + sf_homSuffix);
    }
    else {
      Set<String> alleles = new TreeSet<>(HaplotypeNameComparator.getComparator());
      if (!getAllele1().isReference()) {
        alleles.add(getAllele1().printDisplay() + sf_hetSuffix);
      }
      if (!getAllele2().isReference()) {
        alleles.add(getAllele2().printDisplay() + sf_hetSuffix);
      }
      return alleles.stream();
    }
  }

  /**
   * Gets a variant used to make this diplotype call
   * @return a Variant used in this call
   */
  public VariantReport getVariant() {
    return m_variant;
  }

  public void setVariant(VariantReport variant) {
    m_variant = variant;
  }

  @Override
  public int compareTo(@Nonnull Diplotype o) {
    int rez = ObjectUtils.compare(getGene(), o.getGene());
    if (rez != 0) {
      return rez;
    }

    rez = ObjectUtils.compare(getAllele1(), o.getAllele1());
    if (rez != 0) {
      return rez;
    }

    return ObjectUtils.compare(getAllele2(), o.getAllele2());
  }
}
