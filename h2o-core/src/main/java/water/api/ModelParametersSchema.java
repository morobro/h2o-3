package water.api;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelBuilder.ValidationMessage;
import hex.ModelBuilder.ValidationMessage.MessageType;
import water.AutoBuffer;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.util.Log;
import water.util.PojoUtils;

import java.lang.reflect.Field;

/**
 * An instance of a ModelParameters schema contains the Model build parameters (e.g., K and max_iters for KMeans).
 */
abstract public class ModelParametersSchema<P extends Model.Parameters, S extends ModelParametersSchema<P, S>> extends Schema<P, S> {
  ////////////////////////////////////////
  // NOTE:
  // Parameters must be ordered for the UI
  ////////////////////////////////////////

  /** List of fields in the order in which we want them serialized.  This is the order they will be presented in the UI. */
  abstract public String[] fields();

  // Parameters common to all models:
  @API(help="Destination key for this model; if unset they key is auto-generated.", required = false, direction=API.Direction.INOUT)
  public Key destination_key;

  @API(help="Training frame", direction=API.Direction.INOUT)
  public Frame training_frame;

  @API(help="Validation frame", direction=API.Direction.INOUT)
  public Frame validation_frame;

  // TODO: pass these as a new helper class that contains frame and vec; right now we have no automagic way to
  // know which frame a Vec name corresponds to, so there's hardwired logic in the adaptor which knows that these
  // column names are related to training_frame.
  @API(help="Response column", direction=API.Direction.INOUT)
  public String response_column;

  @API(help="Ignored columns", direction=API.Direction.INOUT)
  public String[] ignored_columns;         // column names to ignore for training

  @API(help="Score validation set on each major model-building iteration; can be slow", direction=API.Direction.INOUT)
  public boolean score_each_iteration;

  public S fillFromImpl(P parms) {
    PojoUtils.copyProperties(this, parms, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES );
    return (S)this;
  }

  // Version&Schema-specific filling into the implementation object
  abstract public P createImpl();

  public static class ValidationMessageBase extends Schema<ModelBuilder.ValidationMessage, ValidationMessageBase> {
    @API(help="Type of validation message (ERROR, WARN, INFO, HIDE)", direction=API.Direction.OUTPUT)
    public String message_type;

    @API(help="Field to which the message applies", direction=API.Direction.OUTPUT)
    public String field_name;

    @API(help="Message text", direction=API.Direction.OUTPUT)
    public String message;

    public ModelBuilder.ValidationMessage createImpl() { return new ModelBuilder.ValidationMessage(MessageType.valueOf(message_type), field_name, message); };

    // Version&Schema-specific filling from the implementation object
    public ValidationMessageBase fillFromImpl(ValidationMessage vm) {
      PojoUtils.copyProperties(this, vm, PojoUtils.FieldNaming.CONSISTENT);
      if (this.field_name.startsWith("_"))
        this.field_name = this.field_name.substring(1);
      else
        Log.warn("Expected all ValidationMessage field_name values to have leading underscores; ignoring: " + field_name);
      return this;
    }
  }

  public static final class ValidationMessageV2 extends ValidationMessageBase {  }

  /**
   * Write the parameters, including their metadata, into an AutoBuffer.  Used by
   * ModelBuilderSchema#writeJSON_impl and ModelSchema#writeJSON_impl.
   */
  public static final AutoBuffer writeParametersJSON( AutoBuffer ab, ModelParametersSchema parameters, ModelParametersSchema default_parameters) {
    String[] fields = parameters.fields();

    // Build ModelParameterSchemaV2 objects for each field, and the call writeJSON on the array
    ModelParameterSchemaV2[] metadata = new ModelParameterSchemaV2[fields.length];

    String field_name = null;
    try {
      for (int i = 0; i < fields.length; i++) {
        field_name = fields[i];
        Field f = parameters.getClass().getField(field_name);

        // TODO: cache a default parameters schema
        ModelParameterSchemaV2 schema = new ModelParameterSchemaV2(parameters, default_parameters, f);
        metadata[i] = schema;
      }
    } catch (NoSuchFieldException e) {
      throw H2O.fail("Caught exception accessing field: " + field_name + " for schema object: " + parameters + ": " + e.toString());
    }

    ab.putJSONA("parameters", metadata);
    return ab;
  }
}
