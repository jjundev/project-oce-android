package com.jjundev.oneclickeng.learning.dialoguelearning.model;

/** Conceptual Bridge section with literal translation, explanation, and Venn diagram */
public class ConceptualBridge {
  private String literalTranslation;
  private String explanation;
  private String vennDiagramGuide;
  private VennDiagram vennDiagram;

  public String getLiteralTranslation() {
    return literalTranslation;
  }

  public void setLiteralTranslation(String literalTranslation) {
    this.literalTranslation = literalTranslation;
  }

  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(String explanation) {
    this.explanation = explanation;
  }

  public String getVennDiagramGuide() {
    return vennDiagramGuide;
  }

  public void setVennDiagramGuide(String vennDiagramGuide) {
    this.vennDiagramGuide = vennDiagramGuide;
  }

  public VennDiagram getVennDiagram() {
    return vennDiagram;
  }

  public void setVennDiagram(VennDiagram vennDiagram) {
    this.vennDiagram = vennDiagram;
  }
}
