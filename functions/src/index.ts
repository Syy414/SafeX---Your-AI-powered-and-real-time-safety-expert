import {onRequest} from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

export const ping = onRequest((req, res) => {
  logger.info("SafeX ping", {method: req.method});
  res.status(200).send("SafeX Functions OK");
});
