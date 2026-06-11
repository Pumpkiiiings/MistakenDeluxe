import java.io.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;

public class FixYaml {
    public static void main(String[] args) throws Exception {
        File dir = new File("MistakenDeluxe-Core/src/main/resources/langs/es");
        for (File f : dir.listFiles()) {
            if (!f.getName().endsWith(".yml")) continue;
            
            byte[] bytes = Files.readAllBytes(f.toPath());
            
            // Remove 0x81
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte b : bytes) {
                if ((b & 0xFF) != 0x81) {
                    out.write(b);
                }
            }
            byte[] newBytes = out.toByteArray();
            
            // Decode as Windows-1252 or fallback to UTF-8
            String text = new String(newBytes, StandardCharsets.UTF_8);
            
            // Replace strings
            text = text.replace("INFORMACI\uFFFD\"N", "INFORMACI\u00D3N");
            text = text.replace("INFORMACI\uFFFDN", "INFORMACI\u00D3N");
            text = text.replace("estad\uFFFDsticas", "estad\u00EDsticas");
            text = text.replace("Mec\u01ECnica", "Mec\u00E1nica");
            text = text.replace("cl\u01ECsico", "cl\u00E1sico");
            text = text.replace("f\u00EDsica", "f\u00EDsica");
            text = text.replace("fsica", "f\u00EDsica");
            text = text.replace("mgico", "m\u00E1gico");
            text = text.replace("F\u01ECcil", "F\u00E1cil");
            text = text.replace("persecucin", "persecuci\u00F3n");
            text = text.replace("Dif\uFFFDcil", "Dif\u00EDcil");
            text = text.replace("Mgica", "M\u00E1gica");
            text = text.replace("T\uFFFDnel", "T\u00FAnel");
            text = text.replace("\uFFFDltimo", "\u00DAltimo");
            text = text.replace("M\uFFFDsica", "M\u00FAsica");
            text = text.replace("Da\uFFFDo", "Da\u00F1o");

            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
            System.out.println("Cleaned " + f.getName());
        }
    }
}
