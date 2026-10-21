async function runPhase2Tests() {
  console.log("=======================================================");
  console.log("✨ Running Phase 2 Context & Multilingual API Tests");
  console.log("=======================================================");

  // 1. Summarize Context Endpoint
  const sumRes = await fetch('http://localhost:8080/v1/ai/context/summarize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'phase2-test-device' },
    body: JSON.stringify({
      content: "Bro kal tak wo project status report and documents bhej dena. Client meeting is scheduled for Wednesday 10 AM.",
      max_bullets: 3
    })
  });
  const sumData = await sumRes.json();
  console.log("\n1. /v1/ai/context/summarize Response:");
  console.log("   Bullets Output:\n" + sumData.summary);
  console.log("   Processed Chars:", sumData.processed_chars);

  // 2. Explain Context Endpoint
  const expRes = await fetch('http://localhost:8080/v1/ai/context/explain', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'phase2-test-device' },
    body: JSON.stringify({
      content: "API rate limiting threshold exceeded. HTTP 429 payload returned."
    })
  });
  const expData = await expRes.json();
  console.log("\n2. /v1/ai/context/explain Response:");
  console.log("   Explanation:", expData.explanation);
  console.log("   Key Terms:", expData.key_terms);

  // 3. Multilingual Translation (Hinglish, Hindi, Spanish, French)
  const langs = ['hinglish', 'hi', 'es', 'fr'];
  for (const lang of langs) {
    const transRes = await fetch('http://localhost:8080/v1/ai/translate/multilingual', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-device-id': 'phase2-test-device' },
      body: JSON.stringify({
        input_text: "I will send the document tomorrow.",
        target_language: lang
      })
    });
    const transData = await transRes.json();
    console.log(`\n3. Multilingual Translation [${transData.target_language_name}]:`);
    console.log(`   Result: "${transData.translated_text}"`);
  }

  console.log("\n=======================================================");
  console.log("✅ Phase 2 Test Suite Execution Completed Successfully");
  console.log("=======================================================");
}

runPhase2Tests();
