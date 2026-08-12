package xyz.mdhv.formanalyser.coach

enum class Provider { ANTHROPIC, OPENAI, GOOGLE, DEEPSEEK, ON_DEVICE, OTHER }
enum class ModelKind { CLOUD, ON_DEVICE }
data class CoachModel(val id:String,val provider:Provider,val displayName:String,val kind:ModelKind,val requiresByok:Boolean,val approxContextTokens:Int){val isFreeHostedChat:Boolean get()=kind==ModelKind.CLOUD&&!requiresByok}
object ModelRegistry {
 val models=listOf(
  CoachModel("claude-opus-4-8",Provider.ANTHROPIC,"Claude Opus 4.8",ModelKind.CLOUD,true,200_000),CoachModel("claude-sonnet-5",Provider.ANTHROPIC,"Claude Sonnet 5",ModelKind.CLOUD,true,200_000),CoachModel("claude-haiku-4-5",Provider.ANTHROPIC,"Claude Haiku 4.5",ModelKind.CLOUD,true,200_000),CoachModel("claude-fable-5",Provider.ANTHROPIC,"Claude Fable 5",ModelKind.CLOUD,true,200_000),
  CoachModel("gpt-5",Provider.OPENAI,"GPT-5",ModelKind.CLOUD,true,400_000),CoachModel("gpt-5-mini",Provider.OPENAI,"GPT-5 mini",ModelKind.CLOUD,true,128_000),
  CoachModel("gemini-2.5-pro",Provider.GOOGLE,"Gemini 2.5 Pro",ModelKind.CLOUD,true,1_000_000),CoachModel("gemini-2.5-flash",Provider.GOOGLE,"Gemini 2.5 Flash",ModelKind.CLOUD,true,1_000_000),
  CoachModel("deepseek-v4-flash",Provider.DEEPSEEK,"DeepSeek V4 Flash",ModelKind.CLOUD,true,128_000),CoachModel("deepseek-v4-pro",Provider.DEEPSEEK,"DeepSeek V4 Pro",ModelKind.CLOUD,true,128_000),
  CoachModel("gemma-3n-e4b",Provider.ON_DEVICE,"Gemma 3n E4B (on-device)",ModelKind.ON_DEVICE,false,8_192),CoachModel("gemma-3n-e2b",Provider.ON_DEVICE,"Gemma 3n E2B (on-device)",ModelKind.ON_DEVICE,false,4_096))
 private val byId=models.associateBy{it.id};fun byId(id:String)=byId[id];fun byProvider(provider:Provider)=models.filter{it.provider==provider};fun cloudModels()=models.filter{it.kind==ModelKind.CLOUD};fun onDeviceModels()=models.filter{it.kind==ModelKind.ON_DEVICE};fun hasFreeHostedTier()=models.any{it.isFreeHostedChat}
}
