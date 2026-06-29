package com.jjundev.oneclickeng.view;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennCircle;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennDiagram;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennIntersection;
import java.util.List;

/**
 * Custom View for rendering a Venn diagram with two overlapping circles
 *
 * <p>Rendering guide: - Two circles with equal radius - Approximately 30% overlap - Left circle
 * items on the left - Right circle items on the right - Intersection items in the middle
 */
public class VennDiagramView extends View {

  private static final int SIDE_ALPHA = 128;
  private static final int INTERSECTION_ALPHA = 180;

  private static final double MIN_PRIMARY_CONTRAST_SIDE = 4.5d;
  private static final double MIN_SUB_CONTRAST_SIDE = 3.0d;
  private static final double MIN_PRIMARY_CONTRAST_INTERSECTION = 4.5d;

  private static final double MIN_SIDE_COLOR_DISTANCE = 50d;
  private static final double MIN_INTERSECTION_COLOR_DISTANCE = 40d;

  private static final int FALLBACK_LEFT_COLOR = 0xFF439B79;
  private static final int FALLBACK_RIGHT_COLOR = 0xFF448DEB;
  private static final int FALLBACK_INTERSECTION_COLOR = 0xFFB869F7;

  private static final int LIGHT_BG_FALLBACK = 0xFFFFFFFF;
  private static final int DARK_BG_FALLBACK = 0xFF1A1B20;
  private static final int LIGHT_PRIMARY_TEXT_FALLBACK = 0xFF353C45;
  private static final int DARK_PRIMARY_TEXT_FALLBACK = 0xFFF2F3F5;
  private static final int LIGHT_SUB_TEXT_FALLBACK = 0xFF676B73;
  private static final int DARK_SUB_TEXT_FALLBACK = 0xFFA9ADB6;

  private VennDiagram vennDiagram;

  // Paints
  private Paint leftCirclePaint;
  private Paint rightCirclePaint;
  private Paint intersectionPaint;
  private Paint labelPaint;
  private Paint sideItemPaint;
  private Paint intersectionItemPaint;
  private Paint strokePaint;

  // Reference colors for contrast checks
  private int lightBackgroundColor;
  private int darkBackgroundColor;
  private int lightPrimaryTextColor;
  private int darkPrimaryTextColor;
  private int lightSubTextColor;
  private int darkSubTextColor;

  // Dimensions
  private float circleRadius;
  private float leftCenterX;
  private float rightCenterX;
  private float centerY;
  private static final float OVERLAP_RATIO = 0.3f;

  public VennDiagramView(Context context) {
    super(context);
    init();
  }

  public VennDiagramView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public VennDiagramView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    resolveContrastReferenceColors();

    // Left circle paint
    leftCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    leftCirclePaint.setStyle(Paint.Style.FILL);
    leftCirclePaint.setColor(withAlpha(FALLBACK_LEFT_COLOR, SIDE_ALPHA));

    // Right circle paint
    rightCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    rightCirclePaint.setStyle(Paint.Style.FILL);
    rightCirclePaint.setColor(withAlpha(FALLBACK_RIGHT_COLOR, SIDE_ALPHA));

    // Intersection paint
    intersectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    intersectionPaint.setStyle(Paint.Style.FILL);
    intersectionPaint.setColor(withAlpha(FALLBACK_INTERSECTION_COLOR, INTERSECTION_ALPHA));

    // Stroke paint for circle outlines
    strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setStrokeWidth(2f);
    strokePaint.setColor(Color.DKGRAY);

    // Label paint (for word labels)
    labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    labelPaint.setTextSize(36f);
    labelPaint.setTextAlign(Paint.Align.CENTER);
    labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
    labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.color_primary_text));

    // Side item paint: left/right list items use color_sub_text
    sideItemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    sideItemPaint.setTextSize(24f);
    sideItemPaint.setTextAlign(Paint.Align.CENTER);
    sideItemPaint.setColor(ContextCompat.getColor(getContext(), R.color.color_sub_text));

    // Intersection item paint: intersection list items use color_primary_text
    intersectionItemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    intersectionItemPaint.setTextSize(24f);
    intersectionItemPaint.setTextAlign(Paint.Align.CENTER);
    intersectionItemPaint.setColor(
        ContextCompat.getColor(getContext(), R.color.color_primary_text));
  }

  private void resolveContrastReferenceColors() {
    lightBackgroundColor =
        resolveColorForMode(R.color.color_background_4, false, LIGHT_BG_FALLBACK);
    darkBackgroundColor = resolveColorForMode(R.color.color_background_4, true, DARK_BG_FALLBACK);
    lightPrimaryTextColor =
        resolveColorForMode(R.color.color_primary_text, false, LIGHT_PRIMARY_TEXT_FALLBACK);
    darkPrimaryTextColor =
        resolveColorForMode(R.color.color_primary_text, true, DARK_PRIMARY_TEXT_FALLBACK);
    lightSubTextColor = resolveColorForMode(R.color.color_sub_text, false, LIGHT_SUB_TEXT_FALLBACK);
    darkSubTextColor = resolveColorForMode(R.color.color_sub_text, true, DARK_SUB_TEXT_FALLBACK);
  }

  private int resolveColorForMode(int colorRes, boolean nightMode, int fallback) {
    try {
      Context baseContext = getContext();
      if (baseContext == null) {
        return fallback;
      }
      Configuration configuration =
          new Configuration(baseContext.getResources().getConfiguration());
      configuration.uiMode =
          (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
              | (nightMode ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
      Context modeContext = baseContext.createConfigurationContext(configuration);
      return ContextCompat.getColor(modeContext, colorRes);
    } catch (Exception e) {
      return fallback;
    }
  }

  /** Set the Venn diagram data and refresh the view */
  public void setVennDiagram(VennDiagram diagram) {
    this.vennDiagram = diagram;
    if (diagram == null) {
      leftCirclePaint.setColor(withAlpha(FALLBACK_LEFT_COLOR, SIDE_ALPHA));
      rightCirclePaint.setColor(withAlpha(FALLBACK_RIGHT_COLOR, SIDE_ALPHA));
      intersectionPaint.setColor(withAlpha(FALLBACK_INTERSECTION_COLOR, INTERSECTION_ALPHA));
      invalidate();
      return;
    }

    int leftInputColor = resolveCircleColor(diagram.getLeftCircle(), FALLBACK_LEFT_COLOR);
    int rightInputColor = resolveCircleColor(diagram.getRightCircle(), FALLBACK_RIGHT_COLOR);
    int intersectionInputColor =
        resolveIntersectionColor(diagram.getIntersection(), FALLBACK_INTERSECTION_COLOR);

    int resolvedLeftColor = chooseSideColor(leftInputColor, FALLBACK_LEFT_COLOR, null);
    int resolvedRightColor =
        chooseSideColor(rightInputColor, FALLBACK_RIGHT_COLOR, resolvedLeftColor);

    // Keep left and right circles distinguishable. Prefer replacing right as requested.
    if (colorDistance(resolvedLeftColor, resolvedRightColor) < MIN_SIDE_COLOR_DISTANCE) {
      resolvedRightColor =
          chooseSideColor(FALLBACK_RIGHT_COLOR, FALLBACK_RIGHT_COLOR, resolvedLeftColor);
    }

    int resolvedIntersectionColor =
        chooseIntersectionColor(intersectionInputColor, resolvedLeftColor, resolvedRightColor);

    leftCirclePaint.setColor(withAlpha(resolvedLeftColor, SIDE_ALPHA));
    rightCirclePaint.setColor(withAlpha(resolvedRightColor, SIDE_ALPHA));
    intersectionPaint.setColor(withAlpha(resolvedIntersectionColor, INTERSECTION_ALPHA));

    invalidate();
  }

  private static int withAlpha(int color, int alpha) {
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
  }

  private int chooseSideColor(int preferredColor, int fallbackColor, @Nullable Integer avoidColor) {
    int[] candidates =
        new int[] {
          preferredColor,
          fallbackColor,
          adjustLightness(preferredColor, 0.85f),
          adjustLightness(preferredColor, 1.15f),
          adjustHue(preferredColor, 20f),
          adjustHue(preferredColor, -20f),
          adjustHue(fallbackColor, 20f),
          FALLBACK_LEFT_COLOR,
          FALLBACK_RIGHT_COLOR
        };

    for (int candidate : candidates) {
      if (!passesSideContrast(candidate)) {
        continue;
      }
      if (avoidColor != null && colorDistance(candidate, avoidColor) < MIN_SIDE_COLOR_DISTANCE) {
        continue;
      }
      return candidate;
    }

    return fallbackColor;
  }

  private int chooseIntersectionColor(int preferredColor, int leftColor, int rightColor) {
    int[] candidates =
        new int[] {
          preferredColor,
          FALLBACK_INTERSECTION_COLOR,
          adjustHue(preferredColor, 24f),
          adjustHue(preferredColor, -24f),
          adjustLightness(preferredColor, 0.9f),
          adjustLightness(preferredColor, 1.1f)
        };

    for (int candidate : candidates) {
      if (!passesIntersectionContrast(candidate)) {
        continue;
      }
      if (colorDistance(candidate, leftColor) < MIN_INTERSECTION_COLOR_DISTANCE
          || colorDistance(candidate, rightColor) < MIN_INTERSECTION_COLOR_DISTANCE) {
        continue;
      }
      return candidate;
    }

    for (int candidate : candidates) {
      if (passesIntersectionContrast(candidate)) {
        return candidate;
      }
    }

    return FALLBACK_INTERSECTION_COLOR;
  }

  private boolean passesSideContrast(int circleColor) {
    int lightFill = blendWithBackground(circleColor, lightBackgroundColor, SIDE_ALPHA);
    int darkFill = blendWithBackground(circleColor, darkBackgroundColor, SIDE_ALPHA);

    double minPrimaryContrast =
        Math.min(
            contrastRatio(lightPrimaryTextColor, lightFill),
            contrastRatio(darkPrimaryTextColor, darkFill));
    double minSubContrast =
        Math.min(
            contrastRatio(lightSubTextColor, lightFill), contrastRatio(darkSubTextColor, darkFill));

    return minPrimaryContrast >= MIN_PRIMARY_CONTRAST_SIDE
        && minSubContrast >= MIN_SUB_CONTRAST_SIDE;
  }

  private boolean passesIntersectionContrast(int circleColor) {
    int lightFill = blendWithBackground(circleColor, lightBackgroundColor, INTERSECTION_ALPHA);
    int darkFill = blendWithBackground(circleColor, darkBackgroundColor, INTERSECTION_ALPHA);
    double minPrimaryContrast =
        Math.min(
            contrastRatio(lightPrimaryTextColor, lightFill),
            contrastRatio(darkPrimaryTextColor, darkFill));
    return minPrimaryContrast >= MIN_PRIMARY_CONTRAST_INTERSECTION;
  }

  private static int adjustHue(int color, float hueDelta) {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    hsv[0] = (hsv[0] + hueDelta + 360f) % 360f;
    return Color.HSVToColor(hsv);
  }

  private static int adjustLightness(int color, float factor) {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    hsv[2] = clamp(hsv[2] * factor, 0.25f, 0.95f);
    return Color.HSVToColor(hsv);
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static double colorDistance(int colorA, int colorB) {
    int dr = Color.red(colorA) - Color.red(colorB);
    int dg = Color.green(colorA) - Color.green(colorB);
    int db = Color.blue(colorA) - Color.blue(colorB);
    return Math.sqrt(dr * dr + dg * dg + db * db);
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

  private static int resolveCircleColor(@Nullable VennCircle circle, int fallback) {
    if (circle == null) {
      return fallback;
    }
    return parseHexColorOrFallback(circle.getColor(), fallback);
  }

  private static int resolveIntersectionColor(
      @Nullable VennIntersection intersection, int fallback) {
    if (intersection == null) {
      return fallback;
    }
    return parseHexColorOrFallback(intersection.getColor(), fallback);
  }

  private static int parseHexColorOrFallback(@Nullable String colorHex, int fallback) {
    if (colorHex == null) {
      return fallback;
    }
    try {
      return Color.parseColor(colorHex.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    calculateDimensions(w, h);
  }

  private void calculateDimensions(int width, int height) {
    float padding = 20f;
    float availableWidth = width - 2 * padding;
    float availableHeight = height - 2 * padding;

    // Calculate radius based on available space
    // With 30% overlap, total width = 2 * radius + (1 - OVERLAP_RATIO) * 2 * radius = radius * (4 -
    // 2 * OVERLAP_RATIO)
    // Actually: total width = 2 * (2 * radius - overlap) = 2 * (2r - 0.3 * 2r) = 2 * 1.4r = 2.8r
    // So: 2 * radius * (2 - OVERLAP_RATIO) = availableWidth
    // radius = availableWidth / (2 * (2 - OVERLAP_RATIO))
    float widthBasedRadius = availableWidth / (2 * (2 - OVERLAP_RATIO));
    float heightBasedRadius = availableHeight / 2;

    circleRadius = Math.min(widthBasedRadius, heightBasedRadius);

    centerY = height / 2f;

    // Calculate center positions for 30% overlap
    float offset = circleRadius * (1 - OVERLAP_RATIO);
    leftCenterX = width / 2f - offset;
    rightCenterX = width / 2f + offset;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (vennDiagram == null) {
      return;
    }

    // Draw left circle
    canvas.drawCircle(leftCenterX, centerY, circleRadius, leftCirclePaint);
    canvas.drawCircle(leftCenterX, centerY, circleRadius, strokePaint);

    // Draw right circle
    canvas.drawCircle(rightCenterX, centerY, circleRadius, rightCirclePaint);
    canvas.drawCircle(rightCenterX, centerY, circleRadius, strokePaint);

    // Draw intersection with different color (overlap area)
    drawIntersection(canvas);

    // Draw labels and items
    drawLabelsAndItems(canvas);
  }

  private void drawIntersection(Canvas canvas) {
    if (vennDiagram.getIntersection() == null) {
      return;
    }

    // Create paths for both circles
    Path leftPath = new Path();
    leftPath.addCircle(leftCenterX, centerY, circleRadius, Path.Direction.CW);

    Path rightPath = new Path();
    rightPath.addCircle(rightCenterX, centerY, circleRadius, Path.Direction.CW);

    // Intersection is where both paths overlap
    Path intersectionPath = new Path();
    intersectionPath.op(leftPath, rightPath, Path.Op.INTERSECT);

    canvas.drawPath(intersectionPath, intersectionPaint);
  }

  private void drawLabelsAndItems(Canvas canvas) {
    float labelOffset = circleRadius * 0.7f;
    float itemStartY = centerY - circleRadius * 0.3f;
    float itemLineHeight = 28f;

    // Left circle label and items
    if (vennDiagram.getLeftCircle() != null) {
      String leftWord = vennDiagram.getLeftCircle().getWord();
      if (leftWord != null) {
        canvas.drawText(
            leftWord, leftCenterX - labelOffset * 0.3f, centerY - circleRadius * 0.5f, labelPaint);
      }

      List<String> leftItems = vennDiagram.getLeftCircle().getItems();
      if (leftItems != null) {
        float y = itemStartY;
        float x = leftCenterX - circleRadius * 0.4f;
        for (String item : leftItems) {
          canvas.drawText("• " + item, x, y, sideItemPaint);
          y += itemLineHeight;
        }
      }
    }

    // Right circle label and items
    if (vennDiagram.getRightCircle() != null) {
      String rightWord = vennDiagram.getRightCircle().getWord();
      if (rightWord != null) {
        canvas.drawText(
            rightWord,
            rightCenterX + labelOffset * 0.3f,
            centerY - circleRadius * 0.5f,
            labelPaint);
      }

      List<String> rightItems = vennDiagram.getRightCircle().getItems();
      if (rightItems != null) {
        float y = itemStartY;
        float x = rightCenterX + circleRadius * 0.4f;
        for (String item : rightItems) {
          canvas.drawText("• " + item, x, y, sideItemPaint);
          y += itemLineHeight;
        }
      }
    }

    // Intersection items
    if (vennDiagram.getIntersection() != null) {
      List<String> intersectionItems = vennDiagram.getIntersection().getItems();
      if (intersectionItems != null) {
        float y = centerY + circleRadius * 0.2f;
        float x = (leftCenterX + rightCenterX) / 2f;
        for (String item : intersectionItems) {
          canvas.drawText(item, x, y, intersectionItemPaint);
          y += itemLineHeight;
        }
      }
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int desiredWidth = 400;
    int desiredHeight = 200;

    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);

    int width;
    int height;

    if (widthMode == MeasureSpec.EXACTLY) {
      width = widthSize;
    } else if (widthMode == MeasureSpec.AT_MOST) {
      width = Math.min(desiredWidth, widthSize);
    } else {
      width = desiredWidth;
    }

    if (heightMode == MeasureSpec.EXACTLY) {
      height = heightSize;
    } else if (heightMode == MeasureSpec.AT_MOST) {
      height = Math.min(desiredHeight, heightSize);
    } else {
      height = desiredHeight;
    }

    setMeasuredDimension(width, height);
  }
}
