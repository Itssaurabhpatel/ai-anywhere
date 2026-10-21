async function runPhase3Tests() {
  console.log("=======================================================");
  console.log("✨ Running Phase 3 Workflow Automation & Billing API Tests");
  console.log("=======================================================");

  // 1. Task Extraction Test
  const taskRes = await fetch('http://localhost:8080/v1/ai/productivity/extract-tasks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'phase3-test-device' },
    body: JSON.stringify({
      content: "Bro kal tak wo document bhej dena. Also schedule the client meeting for Wednesday 10 AM asap."
    })
  });
  const taskData = await taskRes.json();
  console.log("\n1. /v1/ai/productivity/extract-tasks Response:");
  console.log("   Tasks Found Count:", taskData.extracted_tasks_count);
  console.log("   Formatted Checklist:\n" + taskData.formatted_checklist);

  // 2. Voice Polishing Test
  const voiceRes = await fetch('http://localhost:8080/v1/ai/voice/polish', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'phase3-test-device' },
    body: JSON.stringify({
      raw_transcription: "um hello sir i want i want to ask about the report details"
    })
  });
  const voiceData = await voiceRes.json();
  console.log("\n2. /v1/ai/voice/polish Response:");
  console.log("   Raw Input: '" + voiceData.raw_input + "'");
  console.log("   Polished Output: '" + voiceData.polished_output + "'");

  // 3. Subscription Billing Verification Test
  const billingRes = await fetch('http://localhost:8080/v1/billing/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-device-id': 'phase3-test-device' },
    body: JSON.stringify({
      platform: "android",
      product_id: "pro_annual_1499",
      receipt_data: "valid_gplay_receipt_token_xyz"
    })
  });
  const billingData = await billingRes.json();
  console.log("\n3. /v1/billing/verify Response:");
  console.log("   Status:", billingData.status);
  console.log("   Subscription Tier:", billingData.subscription_tier);
  console.log("   Expires At:", billingData.expires_at);

  console.log("\n=======================================================");
  console.log("✅ Phase 3 Test Suite Execution Completed Successfully");
  console.log("=======================================================");
}

runPhase3Tests();
