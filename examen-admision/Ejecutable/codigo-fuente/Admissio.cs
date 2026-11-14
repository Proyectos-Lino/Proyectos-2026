// Admissio - lanzador de escritorio para Windows.
// Abre la plataforma web Admissio en una ventana propia (modo aplicación de Edge o Chrome),
// sin barra de direcciones ni pestañas. La dirección del servidor se puede cambiar en "servidor.txt".
using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Reflection;
using System.Windows.Forms;

[assembly: AssemblyTitle("Admissio")]
[assembly: AssemblyDescription("Plataforma de generación de exámenes de admisión")]
[assembly: AssemblyProduct("Admissio")]
[assembly: AssemblyVersion("1.0.0.0")]
[assembly: AssemblyFileVersion("1.0.0.0")]

static class Programa
{
    const string UrlPorDefecto = "https://admissio.camiluxlym.com/";

    [STAThread]
    static void Main(string[] args)
    {
        Application.EnableVisualStyles();
        string carpeta = AppDomain.CurrentDomain.BaseDirectory;
        string url = LeerUrl(carpeta, args);

        // TLS 1.2 para comprobar servidores HTTPS en Windows con .NET Framework 4.x
        ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;

        while (!ServidorDisponible(url))
        {
            DialogResult r = MessageBox.Show(
                "No se pudo conectar con el servidor de Admissio:\n\n" + url +
                "\n\nVerifique su conexión a internet (o que el servidor local esté encendido) y vuelva a intentarlo.",
                "Admissio", MessageBoxButtons.RetryCancel, MessageBoxIcon.Warning);
            if (r != DialogResult.Retry) return;
        }

        string perfil = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Admissio", "Perfil");
        Directory.CreateDirectory(perfil);

        string navegador = BuscarNavegador();
        try
        {
            if (navegador != null)
            {
                string argumentos = "--app=\"" + url + "\" --user-data-dir=\"" + perfil + "\"" +
                                    " --window-size=1440,900 --no-first-run --no-default-browser-check";
                Process.Start(new ProcessStartInfo(navegador, argumentos) { UseShellExecute = false });
            }
            else
            {
                Process.Start(url); // navegador predeterminado
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show("No se pudo abrir Admissio: " + ex.Message, "Admissio",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    // Prioridad: argumento de línea de comandos > servidor.txt > dirección pública.
    static string LeerUrl(string carpeta, string[] args)
    {
        string url = null;
        if (args.Length > 0 && args[0].StartsWith("http", StringComparison.OrdinalIgnoreCase)) url = args[0];
        string archivo = Path.Combine(carpeta, "servidor.txt");
        if (url == null && File.Exists(archivo))
        {
            foreach (string linea in File.ReadAllLines(archivo))
            {
                string l = linea.Trim();
                if (l.StartsWith("http", StringComparison.OrdinalIgnoreCase)) { url = l; break; }
            }
        }
        if (url == null) url = UrlPorDefecto;
        if (!url.EndsWith("/")) url += "/";
        return url;
    }

    static bool ServidorDisponible(string url)
    {
        try
        {
            HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
            req.Method = "GET";
            req.Timeout = 8000;
            using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                return (int)resp.StatusCode < 500;
        }
        catch (WebException ex)
        {
            HttpWebResponse resp = ex.Response as HttpWebResponse;
            return resp != null && (int)resp.StatusCode < 500;
        }
        catch { return false; }
    }

    static string BuscarNavegador()
    {
        string pf = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        string pf86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
        string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string[] candidatos = {
            Path.Combine(pf86, @"Microsoft\Edge\Application\msedge.exe"),
            Path.Combine(pf, @"Microsoft\Edge\Application\msedge.exe"),
            Path.Combine(pf, @"Google\Chrome\Application\chrome.exe"),
            Path.Combine(pf86, @"Google\Chrome\Application\chrome.exe"),
            Path.Combine(local, @"Google\Chrome\Application\chrome.exe"),
        };
        foreach (string c in candidatos) if (File.Exists(c)) return c;
        return null;
    }
}
