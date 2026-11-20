import { formatErreur } from "@/lib/auth";

/**
 * SSE streaming utility.
 * Consumes a Spring SseEmitter response (data:...\n\n format).
 * The first event may contain "conversationId:<id>" which is extracted and returned.
 */
export async function streamSSE(
  url: string,
  options: RequestInit,
  onToken: (text: string) => void,
  onDone: () => void,
  onError: (error: string) => void
): Promise<string | null> {
  let conversationId: string | null = null;

  try {
    const response = await fetch(url, options);

    if (!response.ok) {
      const errorText = await response.text().catch(() => `Erreur ${response.status}`);
      onError(errorText || `Erreur ${response.status}`);
      return null;
    }

    const reader = response.body?.getReader();
    if (!reader) {
      onError("Impossible de lire le flux de reponse");
      return null;
    }

    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      // Process complete SSE events (separated by \n\n)
      const parts = buffer.split("\n\n");
      // Keep the last (possibly incomplete) part in buffer
      buffer = parts.pop() || "";

      for (const part of parts) {
        const lines = part.split("\n");
        for (const line of lines) {
          if (line.startsWith("data:")) {
            const data = line.slice(5);

            // Check for conversationId in first event
            if (data.startsWith("conversationId:")) {
              conversationId = data.slice("conversationId:".length).trim();
            } else {
              onToken(data);
            }
          }
        }
      }
    }

    // Process any remaining buffer
    if (buffer.trim()) {
      const lines = buffer.split("\n");
      for (const line of lines) {
        if (line.startsWith("data:")) {
          const data = line.slice(5);
          if (!data.startsWith("conversationId:")) {
            onToken(data);
          }
        }
      }
    }

    onDone();
  } catch (err) {
    onError(formatErreur(err));
  }

  return conversationId;
}
