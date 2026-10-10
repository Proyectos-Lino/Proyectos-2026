const fs = require('fs');
const path = require('path');
const { APP_ROOT } = require('./paths');

// Read KB dynamically (chatbot intents — NOT the institutional knowledge)
function getKnowledgeBase() {
  const kbPath = path.join(APP_ROOT, 'data', 'knowledge-base.json');
  try {
    return JSON.parse(fs.readFileSync(kbPath, 'utf-8'));
  } catch (err) {
    console.error('Error reading KB:', err);
    return { intents: [], flows: [], categories: [], teams: [] };
  }
}

// Logic to process a message against KB intents
function processMessageWithKb(message, ticket = null) {
  const kb = getKnowledgeBase();
  const lowerMsg = message.toLowerCase();

  const urgencyKeywords = ['urgente', 'no funciona nada', 'critico', 'toda el area'];
  const isHighUrgency = urgencyKeywords.some(kw => lowerMsg.includes(kw));

  // 1. Check if we are inside an active flow
  if (ticket && ticket.flowState) {
    const flow = kb.flows.find(f => f.id === ticket.flowState.flowId);
    if (flow) {
      ticket.flowState.answers = ticket.flowState.answers || {};
      ticket.flowState.answers[`step_${ticket.flowState.step}`] = message;

      let nextStepNum = ticket.flowState.step + 1;
      const nextStepObj = flow.steps.find(s => s.step === nextStepNum);
      const currentStepObj = flow.steps.find(s => s.step === ticket.flowState.step);

      if (nextStepObj) {
        ticket.flowState.step = nextStepNum;
        return { resolved: false, requiresEscalation: false, response: nextStepObj.question, category: flow.category, flowState: ticket.flowState };
      } else {
        let finalResp = currentStepObj.finalMessage || 'Flujo completado.';
        if (currentStepObj.action === 'calculate_percentage') {
          const previousVal = parseFloat(ticket.flowState.answers['step_1']) || 0;
          const percentage = parseFloat(message) || 0;
          finalResp += (previousVal * percentage) / 100;
        }
        if (isHighUrgency) {
          return { resolved: false, requiresEscalation: true, response: finalResp, category: flow.category, flowState: null, isHighUrgency };
        }
        return { resolved: true, response: finalResp, category: flow.category, flowState: null, isHighUrgency };
      }
    }
  }

  // 2. Check intents
  const intent = kb.intents.find(i => {
    if (Array.isArray(i.trigger)) return i.trigger.some(t => lowerMsg.includes(t));
    return lowerMsg.includes(i.trigger);
  });
  if (intent) {
    if (isHighUrgency) return { resolved: false, requiresEscalation: true, response: intent.response, category: intent.category, isHighUrgency };
    return { resolved: true, response: intent.response, category: intent.category, isHighUrgency };
  }

  // 3. Check flows
  const flow = kb.flows.find(f => lowerMsg.includes(f.trigger));
  if (flow) {
    return { resolved: false, requiresEscalation: false, response: flow.steps[0].question, category: flow.category, flowState: { flowId: flow.id, step: 1, answers: {} }, isHighUrgency };
  }

  // 4. Fallback
  return { resolved: false, requiresEscalation: true, response: 'No encontre una respuesta en la base de conocimiento. El ticket queda abierto para un agente.', category: 'otro', isHighUrgency };
}

module.exports = {
  getKnowledgeBase,
  processMessageWithKb
};
