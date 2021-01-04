using Newtonsoft.Json;

public class PublicKeyResponse
{
    [JsonProperty("publicKey")]
    public string PublicKey { get; set; }
}
