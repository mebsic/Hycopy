package io.github.mebsic.core.model;

public class BossBarMessage {
    private final String id;
    private final String text;
    private final float value;
    private final String scope;
    private final String serverType;
    private final String animationType;
    private final String animationColor;
    private final String firstColor;
    private final String secondColor;
    private final String startColor;
    private final String endColor;

    public BossBarMessage(String id, String text, float value, String scope, String serverType) {
        this(id, text, value, scope, serverType, "", "", "", "", "", "");
    }

    public BossBarMessage(String id,
                          String text,
                          float value,
                          String scope,
                          String serverType,
                          String animationType,
                          String animationColor,
                          String firstColor,
                          String secondColor,
                          String startColor,
                          String endColor) {
        this.id = id == null ? "" : id.trim();
        this.text = text == null ? "" : text;
        this.value = value;
        this.scope = scope == null ? "" : scope.trim();
        this.serverType = serverType == null ? "" : serverType.trim();
        this.animationType = animationType == null ? "" : animationType.trim();
        this.animationColor = animationColor == null ? "" : animationColor.trim();
        this.firstColor = firstColor == null ? "" : firstColor.trim();
        this.secondColor = secondColor == null ? "" : secondColor.trim();
        this.startColor = startColor == null ? "" : startColor.trim();
        this.endColor = endColor == null ? "" : endColor.trim();
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public float getValue() {
        return value;
    }

    public String getScope() {
        return scope;
    }

    public String getServerType() {
        return serverType;
    }

    public String getAnimationType() {
        return animationType;
    }

    public String getAnimationColor() {
        return animationColor;
    }

    public String getFirstColor() {
        return firstColor;
    }

    public String getSecondColor() {
        return secondColor;
    }

    public String getStartColor() {
        return startColor;
    }

    public String getEndColor() {
        return endColor;
    }
}
