import React from 'react';
import { Input, Select } from '../ui/Field';
import {
  buildDataType,
  findDataType,
  isParameterized,
  parseDataType,
  typeBaseName,
} from '../../lib/schema';

/**
 * Data type control: engine catalogue dropdown plus an argument box for sized
 * types, so `VARCHAR(500)` or `DECIMAL(12,4)` stay expressible.
 *
 * The catalogue names parameterised types with a default argument baked in
 * ("VARCHAR(255)"), so the dropdown is keyed on those full names but labelled
 * with the bare base name — the argument lives in its own box next to it.
 */
export function DataTypePicker({ value, onChange, dataTypes, disabled }) {
  const { param } = parseDataType(value);
  const catalogued = findDataType(value, dataTypes);
  const sized = isParameterized(value, dataTypes);

  // Keep a type the catalogue doesn't know about selectable rather than
  // silently snapping the column to another type.
  const options = catalogued
    ? dataTypes
    : [{ name: value, category: 'custom' }, ...dataTypes];

  const changeBase = (catalogueName) => {
    const chosen = dataTypes.find((d) => d.name === catalogueName);
    // Switching base type adopts that type's default argument rather than
    // carrying over one that may be meaningless for it.
    const defaultParam = parseDataType(catalogueName).param;
    onChange(chosen?.parameterized ? buildDataType(catalogueName, defaultParam) : catalogueName);
  };

  return (
    <div className="type-picker">
      <Select
        size="sm"
        value={catalogued ? catalogued.name : value}
        disabled={disabled}
        onChange={(e) => changeBase(e.target.value)}
        aria-label="Data type"
      >
        {options.map((dt) => (
          <option key={dt.name} value={dt.name}>
            {typeBaseName(dt.name)}
            {dt.category ? ` · ${dt.category}` : ''}
          </option>
        ))}
      </Select>

      {sized && (
        <Input
          size="sm"
          value={param}
          disabled={disabled}
          placeholder="255"
          aria-label="Type argument"
          title="Length or precision, e.g. 500 or 12,4"
          onChange={(e) => onChange(buildDataType(value, e.target.value))}
        />
      )}
    </div>
  );
}
