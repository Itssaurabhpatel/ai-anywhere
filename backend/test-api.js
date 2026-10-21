async function runTests() {
  console.log("--- Testing AI Everywhere Backend API ---");

  // 1. Health Check
  const healthRes = await fetch('http://localhost:8080/health');
  const healthData = await healthRes.json();
  console.log("1. /health Response:", healthData);

  // 2. Fix Grammar Execution
  const fixRes = await fetch('http://localhost:8080/v1/ai/execute', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'test-device-01' },
    body: JSON.stringify({
      action_type: 'FIX_GRAMMAR',
      input_text: 'sir kal nhi aa paunga ghar me kam h'
    })
  });
  const fixData = await fixRes.json();
  console.log("\n2. /v1/ai/execute (FIX_GRAMMAR) Response:");
  console.log("   Input: 'sir kal nhi aa paunga ghar me kam h'");
  console.log("   Output Result:", fixData.result_text);
  console.log("   Provider:", fixData.provider_used);
  console.log("   Latency:", fixData.execution_latency_ms, "ms");
  console.log("   Remaining Quota:", fixData.remaining_quota);

  // 3. Smart Reply Execution
  const replyRes = await fetch('http://localhost:8080/v1/ai/execute', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'test-device-01' },
    body: JSON.stringify({
      action_type: 'SMART_REPLY',
      input_text: 'Bro kal tak wo document bhej dena.'
    })
  });
  const replyData = await replyRes.json();
  console.log("\n3. /v1/ai/execute (SMART_REPLY) Response:");
  console.log("   Input: 'Bro kal tak wo document bhej dena.'");
  console.log("   Suggestions:", replyData.suggestions);

  // 4. PII Scrubbing Test
  const piiRes = await fetch('http://localhost:8080/v1/ai/execute', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'test-device-01' },
    body: JSON.stringify({
      action_type: 'REWRITE',
      input_text: 'Call me at 9876543210 or email test@gmail.com for the document'
    })
  });
  const piiData = await piiRes.json();
  console.log("\n4. /v1/ai/execute (PII Redaction Test) Response:");
  console.log("   Redacted PII Count:", piiData.redacted_pii_entities);

  // 5. Sensitive Field Auto-Pause Test
  const secretRes = await fetch('http://localhost:8080/v1/ai/execute', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'test-device-01' },
    body: JSON.stringify({
      action_type: 'FIX_GRAMMAR',
      input_text: 'My password is secret123'
    })
  });
  const secretData = await secretRes.json();
  console.log("\n5. /v1/ai/execute (Sensitive Field Auto-Pause) Response status:", secretRes.status);
  console.log("   Message:", secretData.message);
}

runTests();
