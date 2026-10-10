using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Windows.Automation;

// Run in the ordinary interactive Windows account. SSH authenticates that same SID;
// CurrentUserOnly restricts the named pipe without adding an unauthenticated network listener.
internal static class Program
{
    private const string PipeName = "PersonalWorkspace.Communications.v1";
    private static readonly JsonSerializerOptions Json = new() { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };
    private record Node(string Role, string Name, bool CanInvoke, bool CanSetValue);
    private record Snapshot(string State, string LoginState, bool StructuredMessages, string Revision, List<Node> Nodes);

    private static Task<Snapshot>? inspection;
    private static Snapshot SafeInspect()
    {
        if (inspection is null || inspection.IsCompleted) inspection = Task.Run(Inspect);
        return inspection.Wait(TimeSpan.FromSeconds(5)) ? inspection.Result : new("ACCESSIBILITY_BUSY", "UNKNOWN", false, "", new());
    }

    [STAThread]
    private static void Main(string[] args)
    {
        if (args.SequenceEqual(new[] { "--probe" }))
        {
            var snapshot = SafeInspect();
            Console.WriteLine(JsonSerializer.Serialize(new { snapshot.State, snapshot.LoginState, snapshot.StructuredMessages,
                NodeCount = snapshot.Nodes.Count, Roles = snapshot.Nodes.GroupBy(n => n.Role).ToDictionary(g => g.Key, g => g.Count()),
                InvokeCount = snapshot.Nodes.Count(n => n.CanInvoke), ValueCount = snapshot.Nodes.Count(n => n.CanSetValue) }, Json));
            return;
        }
        if (args.Length != 0) { Environment.ExitCode = 2; return; }
        while (true)
        {
            using var pipe = new NamedPipeServerStream(PipeName, PipeDirection.InOut, 1, PipeTransmissionMode.Byte,
                PipeOptions.CurrentUserOnly | PipeOptions.Asynchronous);
            pipe.WaitForConnection();
            using var reader = new StreamReader(pipe, new UTF8Encoding(false), false, 1024, true);
            using var writer = new StreamWriter(pipe, new UTF8Encoding(false), 1024, true) { AutoFlush = true };
            try
            {
                using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
                var read = reader.ReadLineAsync(timeout.Token).AsTask().GetAwaiter().GetResult();
                if (read is null || read.Length > 1024) throw new InvalidDataException();
                using var input = JsonDocument.Parse(read);
                string? operation = input.RootElement.GetProperty("operation").GetString();
                if (operation is not ("status" or "snapshot")) throw new InvalidDataException();
                var result = SafeInspect();
                if (operation == "status") result = result with { Nodes = new List<Node>() };
                writer.WriteLine(JsonSerializer.Serialize(result, Json));
            }
            catch { try { writer.WriteLine("{\"state\":\"UNAVAILABLE\",\"loginState\":\"UNKNOWN\",\"structuredMessages\":false,\"revision\":\"\",\"nodes\":[]}"); } catch { } }
        }
    }

    private static Snapshot Inspect()
    {
        var nodes = new List<Node>();
        try
        {
            using var process = Process.GetProcessesByName("KakaoTalk").FirstOrDefault(p => p.MainWindowHandle != IntPtr.Zero);
            if (process is null) return new("APP_NOT_RUNNING_OR_NO_WINDOW", "UNKNOWN", false, "", nodes);
            var root = AutomationElement.FromHandle(process.MainWindowHandle);
            var queue = new Queue<(AutomationElement Element, int Depth)>(); queue.Enqueue((root, 0));
            var timer = Stopwatch.StartNew();
            int totalCharacters = 0;
            while (queue.Count > 0 && nodes.Count < 300 && timer.Elapsed < TimeSpan.FromSeconds(3))
            {
                var (element, depth) = queue.Dequeue();
                var current = element.Current;
                if (current.ProcessId != process.Id || current.IsPassword || current.IsOffscreen) continue;
                string role = current.ControlType.ProgrammaticName;
                string name = current.Name ?? "";
                if (name.Length > 2000) name = name[..2000];
                totalCharacters += name.Length;
                if (totalCharacters > 100000) break;
                // Editable values are never read. UIA availability does not prove chat semantics.
                nodes.Add(new(role, name, element.TryGetCurrentPattern(InvokePattern.Pattern, out _),
                    element.TryGetCurrentPattern(ValuePattern.Pattern, out _)));
                if (depth >= 12) continue;
                var walker = TreeWalker.ControlViewWalker;
                var child = walker.GetFirstChild(element);
                for (int i = 0; child is not null && i < 100 && queue.Count < 600; i++)
                {
                    queue.Enqueue((child, depth + 1)); child = walker.GetNextSibling(child);
                }
            }
            string revision = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(nodes, Json))));
            return new("ACCESSIBILITY_OBSERVED", "UNKNOWN", false, revision, nodes);
        }
        catch { return new("ACCESSIBILITY_UNAVAILABLE", "UNKNOWN", false, "", new()); }
    }
}
