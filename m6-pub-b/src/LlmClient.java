public interface LlmClient {
    String complete(String prompt);

    /** Offline clients estimate usage; live adapters override with metered tokens. */
    default Reply completeWithUsage(String prompt) {
        String text = complete(prompt);
        return new Reply(text, (prompt.length() + 3L) / 4,
                (text.length() + 3L) / 4, "estimated: ceil(characters/4)");
    }

    record Reply(String text, long inputTokens, long outputTokens, String accounting) {
        public Reply {
            if (text == null || inputTokens < 0 || outputTokens < 0) {
                throw new IllegalArgumentException("Invalid model reply/usage");
            }
        }
        public long tokens() { return Math.addExact(inputTokens, outputTokens); }
    }
}
