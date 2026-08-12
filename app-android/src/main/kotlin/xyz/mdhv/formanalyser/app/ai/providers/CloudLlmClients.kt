package xyz.mdhv.formanalyser.app.ai.providers
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.LlmClient
import xyz.mdhv.formanalyser.coach.Provider
object CloudLlmClients {
 fun handles(provider:Provider)=when(provider){Provider.ANTHROPIC,Provider.OPENAI,Provider.GOOGLE,Provider.DEEPSEEK->true;Provider.ON_DEVICE,Provider.OTHER->false}
 fun forProvider(provider:Provider,apiKeyLookup:(Provider)->String?):LlmClient?=when(provider){Provider.ANTHROPIC->AnthropicClient{apiKeyLookup(Provider.ANTHROPIC)};Provider.OPENAI->OpenAiClient{apiKeyLookup(Provider.OPENAI)};Provider.GOOGLE->GoogleClient{apiKeyLookup(Provider.GOOGLE)};Provider.DEEPSEEK->DeepSeekClient{apiKeyLookup(Provider.DEEPSEEK)};Provider.ON_DEVICE,Provider.OTHER->null}
 fun forModel(model:CoachModel,apiKeyLookup:(Provider)->String?)=forProvider(model.provider,apiKeyLookup)
}
