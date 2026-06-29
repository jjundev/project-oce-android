package com.jjundev.oneclickeng.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.core.content.ContextCompat;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennCircle;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennDiagram;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennIntersection;
import com.jjundev.oneclickeng.manager_gemini.SentenceFeedbackManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class VennColorContrastGuardTest {

  private static final int SIDE_ALPHA = 128;
  private static final int INTERSECTION_ALPHA = 180;

  private static final int FALLBACK_LEFT = Color.parseColor("#439B79");
  private static final int FALLBACK_RIGHT = Color.parseColor("#448DEB");
  private static final int FALLBACK_INTERSECTION = Color.parseColor("#B869F7");

  @Test
  public void vennTextPaints_useLightModeResourceColors() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    VennDiagramView view = new VennDiagramView(context);

    Paint labelPaint = getPaint(view, "labelPaint");
    Paint sideItemPaint = getPaint(view, "sideItemPaint");
    Paint intersectionItemPaint = getPaint(view, "intersectionItemPaint");

    assertEquals(
        ContextCompat.getColor(context, R.color.color_primary_text), labelPaint.getColor());
    assertEquals(ContextCompat.getColor(context, R.color.color_sub_text), sideItemPaint.getColor());
    assertEquals(
        ContextCompat.getColor(context, R.color.color_primary_text),
        intersectionItemPaint.getColor());
  }

  @Test
  @Config(qualifiers = "night")
  public void vennTextPaints_useNightModeResourceColors() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    VennDiagramView view = new VennDiagramView(context);

    Paint labelPaint = getPaint(view, "labelPaint");
    Paint sideItemPaint = getPaint(view, "sideItemPaint");
    Paint intersectionItemPaint = getPaint(view, "intersectionItemPaint");

    assertEquals(
        ContextCompat.getColor(context, R.color.color_primary_text), labelPaint.getColor());
    assertEquals(ContextCompat.getColor(context, R.color.color_sub_text), sideItemPaint.getColor());
    assertEquals(
        ContextCompat.getColor(context, R.color.color_primary_text),
        intersectionItemPaint.getColor());
  }

  @Test
  public void setVennDiagram_lowContrastAndSimilarColors_areAutoAdjusted() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    VennDiagramView view = new VennDiagramView(context);

    VennDiagram diagram = new VennDiagram();
    VennCircle left = new VennCircle();
    left.setColor("#9C27B0");
    VennCircle right = new VennCircle();
    right.setColor("#9C27B0");
    VennIntersection intersection = new VennIntersection();
    intersection.setColor("#8BC34A");
    diagram.setLeftCircle(left);
    diagram.setRightCircle(right);
    diagram.setIntersection(intersection);

    view.setVennDiagram(diagram);

    int leftPaintColor = getPaint(view, "leftCirclePaint").getColor();
    int rightPaintColor = getPaint(view, "rightCirclePaint").getColor();
    int intersectionPaintColor = getPaint(view, "intersectionPaint").getColor();

    int originalSideColor = Color.argb(SIDE_ALPHA, 156, 39, 176); // #9C27B0 with side alpha.
    assertNotEquals(originalSideColor, rightPaintColor);

    int leftBase =
        Color.rgb(
            Color.red(leftPaintColor), Color.green(leftPaintColor), Color.blue(leftPaintColor));
    int rightBase =
        Color.rgb(
            Color.red(rightPaintColor), Color.green(rightPaintColor), Color.blue(rightPaintColor));
    assertTrue(colorDistance(leftBase, rightBase) >= 50d);

    assertSideContrastThresholdsMet(context, leftBase);
    assertSideContrastThresholdsMet(context, rightBase);

    int intersectionBase =
        Color.rgb(
            Color.red(intersectionPaintColor),
            Color.green(intersectionPaintColor),
            Color.blue(intersectionPaintColor));
    assertIntersectionContrastThresholdMet(context, intersectionBase);
  }

  @Test
  public void setVennDiagram_invalidColors_fallBackToSafePalette() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    VennDiagramView view = new VennDiagramView(context);

    VennDiagram diagram = new VennDiagram();
    VennCircle left = new VennCircle();
    left.setColor("invalid-color");
    VennCircle right = new VennCircle();
    right.setColor("");
    VennIntersection intersection = new VennIntersection();
    intersection.setColor(null);
    diagram.setLeftCircle(left);
    diagram.setRightCircle(right);
    diagram.setIntersection(intersection);

    view.setVennDiagram(diagram);

    int leftPaintColor = getPaint(view, "leftCirclePaint").getColor();
    int rightPaintColor = getPaint(view, "rightCirclePaint").getColor();
    int intersectionPaintColor = getPaint(view, "intersectionPaint").getColor();

    assertEquals(withAlpha(FALLBACK_LEFT, SIDE_ALPHA), leftPaintColor);
    assertEquals(withAlpha(FALLBACK_RIGHT, SIDE_ALPHA), rightPaintColor);
    assertEquals(withAlpha(FALLBACK_INTERSECTION, INTERSECTION_ALPHA), intersectionPaintColor);
  }

  @Test
  public void prompts_includeVennAccessibilityRules_inAssetsAndDummy() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    String systemPromptAsset = readAsset(context, "prompts/sentence_feedback/system_prompt.md");
    String contextMaterialAsset =
        readAsset(context, "prompts/sentence_feedback/context_material.md");

    SentenceFeedbackManager manager = new SentenceFeedbackManager(context, "", "");
    String systemPromptDummy = invokePrivatePromptMethod(manager, "buildSystemPrompt_Dummy");
    String contextMaterialDummy = invokePrivatePromptMethod(manager, "buildContextMaterial_Dummy");

    String requiredMarker = "Venn Diagram Accessibility Constraints";
    assertTrue(systemPromptAsset.contains(requiredMarker));
    assertTrue(contextMaterialAsset.contains(requiredMarker));
    assertTrue(systemPromptDummy.contains(requiredMarker));
    assertTrue(contextMaterialDummy.contains(requiredMarker));

    assertTrue(systemPromptAsset.contains(">= 4.5"));
    assertTrue(systemPromptAsset.contains(">= 3.0"));
    assertTrue(contextMaterialAsset.contains(">= 4.5"));
    assertTrue(contextMaterialAsset.contains(">= 3.0"));
  }

  private static Paint getPaint(VennDiagramView view, String fieldName) throws Exception {
    Field field = VennDiagramView.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Paint) field.get(view);
  }

  private static String readAsset(Context context, String assetPath) throws Exception {
    StringBuilder builder = new StringBuilder();
    try (InputStream is = context.getAssets().open(assetPath);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line).append('\n');
      }
    }
    return builder.toString();
  }

  private static String invokePrivatePromptMethod(
      SentenceFeedbackManager manager, String methodName) throws Exception {
    Method method = SentenceFeedbackManager.class.getDeclaredMethod(methodName);
    method.setAccessible(true);
    return (String) method.invoke(manager);
  }

  private static void assertSideContrastThresholdsMet(Context context, int circleColor) {
    int lightBg = resolveColorForMode(context, R.color.color_background_4, false);
    int darkBg = resolveColorForMode(context, R.color.color_background_4, true);
    int lightPrimary = resolveColorForMode(context, R.color.color_primary_text, false);
    int darkPrimary = resolveColorForMode(context, R.color.color_primary_text, true);
    int lightSub = resolveColorForMode(context, R.color.color_sub_text, false);
    int darkSub = resolveColorForMode(context, R.color.color_sub_text, true);

    int lightFill = blendWithBackground(circleColor, lightBg, SIDE_ALPHA);
    int darkFill = blendWithBackground(circleColor, darkBg, SIDE_ALPHA);

    double minPrimary =
        Math.min(contrastRatio(lightPrimary, lightFill), contrastRatio(darkPrimary, darkFill));
    double minSub = Math.min(contrastRatio(lightSub, lightFill), contrastRatio(darkSub, darkFill));

    assertTrue("Primary contrast must be >= 4.5 but was " + minPrimary, minPrimary >= 4.5d);
    assertTrue("Sub contrast must be >= 3.0 but was " + minSub, minSub >= 3.0d);
  }

  private static void assertIntersectionContrastThresholdMet(Context context, int circleColor) {
    int lightBg = resolveColorForMode(context, R.color.color_background_4, false);
    int darkBg = resolveColorForMode(context, R.color.color_background_4, true);
    int lightPrimary = resolveColorForMode(context, R.color.color_primary_text, false);
    int darkPrimary = resolveColorForMode(context, R.color.color_primary_text, true);

    int lightFill = blendWithBackground(circleColor, lightBg, INTERSECTION_ALPHA);
    int darkFill = blendWithBackground(circleColor, darkBg, INTERSECTION_ALPHA);

    double minPrimary =
        Math.min(contrastRatio(lightPrimary, lightFill), contrastRatio(darkPrimary, darkFill));
    assertTrue(
        "Intersection primary contrast must be >= 4.5 but was " + minPrimary, minPrimary >= 4.5d);
  }

  private static int resolveColorForMode(Context context, int colorRes, boolean nightMode) {
    Configuration configuration = new Configuration(context.getResources().getConfiguration());
    configuration.uiMode =
        (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
            | (nightMode ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
    Context modeContext = context.createConfigurationContext(configuration);
    return ContextCompat.getColor(modeContext, colorRes);
  }

  private static int blendWithBackground(int foregroundColor, int backgroundColor, int alpha) {
    int r = (Color.red(foregroundColor) * alpha + Color.red(backgroundColor) * (255 - alpha)) / 255;
    int g =
        (Color.green(foregroundColor) * alpha + Color.green(backgroundColor) * (255 - alpha)) / 255;
    int b =
        (Color.blue(foregroundColor) * alpha + Color.blue(backgroundColor) * (255 - alpha)) / 255;
    return Color.rgb(r, g, b);
  }

  private static double contrastRatio(int textColor, int backgroundColor) {
    double textLum = relativeLuminance(textColor);
    double backgroundLum = relativeLuminance(backgroundColor);
    double lighter = Math.max(textLum, backgroundLum);
    double darker = Math.min(textLum, backgroundLum);
    return (lighter + 0.05d) / (darker + 0.05d);
  }

  private static double relativeLuminance(int color) {
    double r = linearize(Color.red(color) / 255.0d);
    double g = linearize(Color.green(color) / 255.0d);
    double b = linearize(Color.blue(color) / 255.0d);
    return (0.2126d * r) + (0.7152d * g) + (0.0722d * b);
  }

  private static double linearize(double channel) {
    return channel <= 0.04045d ? channel / 12.92d : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
  }

  private static int withAlpha(int color, int alpha) {
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
  }

  private static double colorDistance(int colorA, int colorB) {
    int dr = Color.red(colorA) - Color.red(colorB);
    int dg = Color.green(colorA) - Color.green(colorB);
    int db = Color.blue(colorA) - Color.blue(colorB);
    return Math.sqrt(dr * dr + dg * dg + db * db);
  }
}
