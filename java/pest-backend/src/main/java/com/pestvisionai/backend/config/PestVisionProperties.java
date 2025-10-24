package com.pestvisionai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pestvision")
public class PestVisionProperties {

    private final Vision vision = new Vision();
    private final Spray spray = new Spray();
    private final Ai ai = new Ai();

    public Vision getVision() {
        return vision;
    }

    public Spray getSpray() {
        return spray;
    }

    public Ai getAi() {
        return ai;
    }

    public static class Vision {
        private String streamUrl = "http://localhost:8000/video/feed";

        public String getStreamUrl() {
            return streamUrl;
        }

        public void setStreamUrl(String streamUrl) {
            this.streamUrl = streamUrl;
        }
    }

    public static class Spray {
        private double confidenceThreshold = 0.75;
        private long cooldownSeconds = 30;

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public long getCooldownSeconds() {
            return cooldownSeconds;
        }

        public void setCooldownSeconds(long cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }
    }

    public static class Ai {
        private final Gemini gemini = new Gemini();

        public Gemini getGemini() {
            return gemini;
        }

        public static class Gemini {
            private String apiKey = "";
            private String model = "gemini-2.5-flash";

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }

            public String getModel() {
                return model;
            }

            public void setModel(String model) {
                this.model = model;
            }
        }
    }
}
