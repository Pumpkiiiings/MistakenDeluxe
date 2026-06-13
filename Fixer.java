import java.nio.file.*;
import java.nio.charset.*;
import java.io.File;

public class Fixer {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get("MistakenDeluxe-Core/src/main/resources/langs");
        Files.walk(root).filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
            try {
                byte[] raw = Files.readAllBytes(p);
                // Strip evil bytes: 0x81 (129), 0x8D (141), 0x8F (143), 0x90 (144), 0x9D (157)
                for (int i = 0; i < raw.length; i++) {
                    int b = raw[i] & 0xFF;
                    if (b == 129 || b == 141 || b == 143 || b == 144 || b == 157) {
                        raw[i] = 32; // space
                    }
                }
                Files.write(p, raw);
                
                String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                text = text.replace("Ã¡", "á");
                text = text.replace("Ã©", "é");
                text = text.replace("Ã­", "í");
                text = text.replace("Ã³", "ó");
                text = text.replace("Ãº", "ú");
                text = text.replace("Ã±", "ñ");
                text = text.replace("Ã‘", "Ñ");
                text = text.replace("Â¡", "¡");
                text = text.replace("Â¿", "¿");
                text = text.replace("Â»", "»");
                text = text.replace("Â«", "«");
                text = text.replace("Ã“", "Ó");
                text = text.replaceAll("ESTÃ.*?S", "ESTÁS");
                text = text.replaceAll("ÃšLTIMO", "ÚLTIMO");
                text = text.replaceAll("Ã‰L", "ÉL");
                text = text.replaceAll("ÃšNICO", "ÚNICO");
                text = text.replaceAll("CÃ.*?ZALO", "CÁZALO");
                text = text.replaceAll("CRÃ.*?DITOS", "CRÉDITOS");
                text = text.replaceAll("PREPÃ.*?RATE", "PREPÁRATE");
                text = text.replaceAll("ENERGÃ.*?A", "ENERGÍA");
                text = text.replaceAll("ESTADÃ.*?STICAS", "ESTADÍSTICAS");
                text = text.replaceAll("NÃ.*?CLEO", "NÚCLEO");
                text = text.replaceAll("ELÃ.*?CTRICO", "ELÉCTRICO");
                text = text.replaceAll("â—.*? ", "● ");
                text = text.replaceAll("â.*?¤", "❤");
                text = text.replaceAll("â.*?”", "⚔");
                text = text.replaceAll("â.*?✔", "✔");
                text = text.replaceAll("â.*?¶", "▶");
                text = text.replaceAll("â.*? ", "⚠");
                text = text.replace("Ãš", "Ú");
                text = text.replace("Ã‰", "É");
                // Any remaining Ã without context is usually Á or Í. Let's look for specific words:
                text = text.replace("TÃ TULOS", "TÍTULOS");
                text = text.replace("MENÃšS", "MENÚS");
                text = text.replace("ESTÁMIRANDO", "ESTÁ MIRANDO");
                text = text.replace("ESTÁCERCA", "ESTÁ CERCA");
                
                Files.write(p, text.getBytes(StandardCharsets.UTF_8));
                System.out.println("Fixed: " + p);
            } catch (Exception e) {}
        });
    }
}
