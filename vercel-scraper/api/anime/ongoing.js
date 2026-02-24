const { getOngoing } = require('../_lib/anichin');
const { sendSuccess, sendError, sendJson } = require('../_lib/response');

module.exports = async (req, res) => {
  if (req.method !== 'GET') {
    return sendJson(res, 405, { success: false, message: 'Method not allowed' });
  }

  try {
    const data = await getOngoing();
    return sendSuccess(res, data);
  } catch (error) {
    return sendError(res, error);
  }
};
